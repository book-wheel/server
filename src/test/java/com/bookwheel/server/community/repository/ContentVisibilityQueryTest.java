package com.bookwheel.server.community.repository;

import com.bookwheel.server.common.cursor.GalleryCursor;
import com.bookwheel.server.community.entity.Post;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContentVisibilityQueryTest {
    private static StandardServiceRegistry registry;
    private static SessionFactory factory;

    @BeforeAll
    static void createMappingsWithoutDatabase() throws ClassNotFoundException {
        registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();
        MetadataSources sources = new MetadataSources(registry);
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (var candidate : scanner.findCandidateComponents("com.bookwheel.server")) {
            sources.addAnnotatedClass(Class.forName(candidate.getBeanClassName()));
        }
        factory = sources.buildMetadata().buildSessionFactory();
    }

    @AfterAll
    static void closeMappings() {
        if (factory != null) factory.close();
        if (registry != null) StandardServiceRegistryBuilder.destroy(registry);
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    @SuppressWarnings("unchecked")
    void everyGalleryPageFiltersBeforeLimitAndFetchesInFixedQueryCount(boolean byIsbn, boolean nextPage) {
        EntityManager manager = mock(EntityManager.class);
        TypedQuery<Long> idsQuery = mock(TypedQuery.class, RETURNS_SELF);
        TypedQuery<Post> postsQuery = mock(TypedQuery.class, RETURNS_SELF);
        List<String> queries = new ArrayList<>();
        try (Session session = factory.openSession()) {
            when(manager.createQuery(anyString(), eq(Long.class))).thenAnswer(call -> {
                String hql = call.getArgument(0);
                queries.add(hql);
                session.createQuery(hql, Long.class).setParameter("userPK", "viewer-pk");
                return idsQuery;
            });
            when(manager.createQuery(anyString(), eq(Post.class))).thenAnswer(call -> {
                session.createQuery((String) call.getArgument(0), Post.class);
                return postsQuery;
            });
            when(idsQuery.getResultList()).thenReturn(List.of(9L, 4L));
            when(postsQuery.getResultList()).thenReturn(List.of(
                    Post.builder().postId(4L).build(), Post.builder().postId(9L).build()));
            var repository = new PostRepositoryImpl(manager);
            GalleryCursor cursor = nextPage ? new GalleryCursor(LocalDateTime.of(2026, 10, 3, 12, 0), 10L) : null;

            List<Post> result = byIsbn
                    ? repository.findGalleryPageByIsbn("9780132350884", cursor, 3, "viewer-pk")
                    : repository.findGalleryPage(cursor, 3, "viewer-pk");

            assertThat(result).extracting(Post::getPostId).containsExactly(9L, 4L);
            assertThat(queries).singleElement().asString().contains(ContentVisibility.POST);
            verify(idsQuery).setParameter("userPK", "viewer-pk");
            verify(idsQuery).setMaxResults(3);
            verify(manager, times(1)).createQuery(anyString(), eq(Long.class));
            verify(manager, times(1)).createQuery(anyString(), eq(Post.class));
            if (byIsbn) verify(idsQuery).setParameter("isbn", "9780132350884");
            if (nextPage) {
                verify(idsQuery).setParameter("cursorCreatedAt", cursor.createdAt());
                verify(idsQuery).setParameter("cursorGalleryId", 10L);
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void galleryCountsUseSameViewerPredicate() {
        EntityManager manager = mock(EntityManager.class);
        TypedQuery<Long> query = mock(TypedQuery.class, RETURNS_SELF);
        try (Session session = factory.openSession()) {
            when(manager.createQuery(anyString(), eq(Long.class))).thenAnswer(call -> {
                String hql = call.getArgument(0);
                assertThat(hql).contains(ContentVisibility.POST);
                session.createQuery(hql, Long.class).setParameter("userPK", "viewer-pk");
                return query;
            });
            when(query.getSingleResult()).thenReturn(2L);
            var repository = new PostRepositoryImpl(manager);
            assertThat(repository.countGalleryPosts("viewer-pk")).isEqualTo(2L);
            assertThat(repository.countGalleryPostsByIsbn("9780132350884", "viewer-pk")).isEqualTo(2L);
            verify(query, times(2)).setParameter("userPK", "viewer-pk");
        }
    }

    @Test
    void detailAndCommentQueriesCompileAgainstActualEntityMappings() {
        try (Session session = factory.openSession()) {
            for (Class<?> repository : List.of(PostRepository.class, PostCommentRepository.class)) {
                for (var method : repository.getDeclaredMethods()) {
                    Query annotation = method.getAnnotation(Query.class);
                    if (annotation == null || !annotation.value().contains(":userPK")) continue;
                    String predicate = repository == PostRepository.class ? ContentVisibility.POST : ContentVisibility.COMMENT;
                    assertThat(annotation.value()).contains(predicate);
                    var query = session.createQuery(annotation.value());
                    query.setParameter("userPK", "viewer-pk");
                    assertThat(query.getParameter("userPK").getParameterType()).isEqualTo(String.class);
                }
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"Post,p", "PostComment,c"})
    void visibilityUsesForeignKeysWithoutDroppingDetachedAuthors(String entity, String alias) {
        List<String> statements = new ArrayList<>();
        try (Session session = factory.withOptions().statementInspector(sql -> {
            statements.add(sql);
            throw new SqlInspected();
        }).openSession()) {
            String predicate = entity.equals("Post") ? ContentVisibility.POST : ContentVisibility.COMMENT;
            var query = session.createQuery("select " + alias + " from " + entity + " " + alias + " where " + predicate);
            query.setParameter("userPK", "viewer-pk");
            assertThatThrownBy(query::getResultList).isInstanceOf(RuntimeException.class);
            assertThat(statements).singleElement().asString()
                    .contains("not exists", "user_block", "blocker_user_pk", "blocked_user_pk", "user_id")
                    .doesNotContain("join users");
        }
    }

    private static class SqlInspected extends RuntimeException {}
}
