package com.bookwheel.server.user.service;

import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserDataPurgeTransactionServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void detachesPostsBeforeCommentsAndDeletesUserLast() {
        UserRepository users = mock(UserRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        S3DeletionQueueService queue = mock(S3DeletionQueueService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC);
        User user = User.builder().nickname("withdrawn").build();
        user.deactivate(LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0));
        when(users.findByUserPKForUpdate(user.getId())).thenReturn(Optional.of(user));
        TypedQuery<String> keys = mock(TypedQuery.class, RETURNS_SELF);
        when(keys.getResultList()).thenReturn(List.of());
        when(entityManager.createQuery(anyString(), eq(String.class))).thenReturn(keys);
        TypedQuery<Long> notifications = mock(TypedQuery.class, RETURNS_SELF);
        when(notifications.getResultList()).thenReturn(List.of());
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(notifications);
        Query other = mock(Query.class, RETURNS_SELF);
        when(entityManager.createQuery(anyString())).thenReturn(other);
        when(entityManager.createNativeQuery(anyString())).thenReturn(other);
        Query posts = mock(Query.class, RETURNS_SELF);
        Query comments = mock(Query.class, RETURNS_SELF);
        when(entityManager.createQuery("update Post post set post.uploader = null where post.uploader.id = :userPK"))
            .thenReturn(posts);
        when(entityManager.createQuery("update PostComment comment set comment.user = null where comment.user.id = :userPK"))
            .thenReturn(comments);

        new UserDataPurgeTransactionService(users, entityManager, queue, clock).purgeIfDue(user.getId());

        var order = inOrder(posts, comments, users);
        order.verify(users).findByUserPKForUpdate(user.getId());
        order.verify(posts).setParameter("userPK", user.getId());
        order.verify(posts).executeUpdate();
        order.verify(comments).setParameter("userPK", user.getId());
        order.verify(comments).executeUpdate();
        order.verify(users).delete(user);
        order.verify(users).flush();
    }
}
