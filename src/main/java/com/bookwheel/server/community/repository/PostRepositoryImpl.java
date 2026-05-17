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

    @Override
    public long countGalleryPosts() {
        return entityManager.createQuery("""
                select count(p)
                from Post p
                where p.images is not empty
                """, Long.class)
            .getSingleResult();
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
}
