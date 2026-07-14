package com.bookwheel.server.community.repository;

import com.bookwheel.server.common.cursor.GalleryCursor;
import com.bookwheel.server.community.entity.Post;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepositoryCustom {

    private final EntityManager entityManager;

    @Override
    public List<Post> findGalleryPage(GalleryCursor cursor, int limit) {
        List<Long> postIds = findGalleryPostIds(cursor, limit);
        if (postIds.isEmpty()) {
            return List.of();
        }
        return fetchPostsByIds(postIds);
    }

    @Override
    public long countGalleryPosts() {
        return entityManager.createQuery("""
                select count(p)
                from Post p
                where p.images is not empty
                """, Long.class)
            .getSingleResult();
    }

    @Override
    public List<Post> findGalleryPageByIsbn(String isbn, GalleryCursor cursor, int limit) {
        List<Long> postIds = findGalleryPostIdsByIsbn(isbn, cursor, limit);
        if (postIds.isEmpty()) {
            return List.of();
        }
        return fetchPostsByIds(postIds);
    }

    @Override
    public long countGalleryPostsByIsbn(String isbn) {
        return entityManager.createQuery("""
                select count(p)
                from Post p
                where p.images is not empty
                and p.bookInfo.isbn = :isbn
                """, Long.class)
            .setParameter("isbn", isbn)
            .getSingleResult();
    }

    // 조회된 postId 순서를 유지하면서 게시물과 연관 엔티티(bookInfo, images)를 한 번에 로딩한다.
    private List<Post> fetchPostsByIds(List<Long> postIds) {
        List<Post> posts = entityManager.createQuery("""
                select distinct p
                from Post p
                join fetch p.bookInfo
                left join fetch p.images
                where p.postId in :postIds
                """, Post.class)
            .setParameter("postIds", postIds)
            .getResultList();

        Map<Long, Integer> sortOrder = new HashMap<>();
        for (int i = 0; i < postIds.size(); i++) {
            sortOrder.put(postIds.get(i), i);
        }

        return posts.stream()
            .sorted(Comparator.comparingInt(post -> sortOrder.get(post.getPostId())))
            .toList();
    }

    private List<Long> findGalleryPostIds(GalleryCursor cursor, int limit) {
        if (cursor == null) {
            return entityManager.createQuery("""
                    select p.postId
                    from Post p
                    where p.images is not empty
                    order by p.createdAt desc, p.postId desc
                    """, Long.class)
                .setMaxResults(limit)
                .getResultList();
        }

        return entityManager.createQuery("""
                select p.postId
                from Post p
                where p.images is not empty
                and (
                    p.createdAt < :cursorCreatedAt
                    or (p.createdAt = :cursorCreatedAt and p.postId < :cursorGalleryId)
                )
                order by p.createdAt desc, p.postId desc
                """, Long.class)
            .setParameter("cursorCreatedAt", cursor.createdAt())
            .setParameter("cursorGalleryId", cursor.galleryId())
            .setMaxResults(limit)
            .getResultList();
    }

    private List<Long> findGalleryPostIdsByIsbn(String isbn, GalleryCursor cursor, int limit) {
        if (cursor == null) {
            return entityManager.createQuery("""
                    select p.postId
                    from Post p
                    where p.images is not empty
                    and p.bookInfo.isbn = :isbn
                    order by p.createdAt desc, p.postId desc
                    """, Long.class)
                .setParameter("isbn", isbn)
                .setMaxResults(limit)
                .getResultList();
        }

        return entityManager.createQuery("""
                select p.postId
                from Post p
                where p.images is not empty
                and p.bookInfo.isbn = :isbn
                and (
                    p.createdAt < :cursorCreatedAt
                    or (p.createdAt = :cursorCreatedAt and p.postId < :cursorGalleryId)
                )
                order by p.createdAt desc, p.postId desc
                """, Long.class)
            .setParameter("isbn", isbn)
            .setParameter("cursorCreatedAt", cursor.createdAt())
            .setParameter("cursorGalleryId", cursor.galleryId())
            .setMaxResults(limit)
            .getResultList();
    }
}
