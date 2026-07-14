package com.bookwheel.server.community.repository;

import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.entity.PostComment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;

public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    List<PostComment> findAllByPost(Post post);

    long countByPost(Post post);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from PostComment comment where comment.post.postId in :postIds")
    // 게시물 삭제 전에 댓글이 참조하는 게시물을 먼저 제거한다.
    void deleteAllByPostIds(@Param("postIds") Collection<Long> postIds);

    // 최신순 첫 페이지 (작성일 내림차순, 동일 시 댓글 ID 내림차순)
    @Query("""
        select c from PostComment c
        join fetch c.user
        where c.post = :post
        order by c.createdAt desc, c.postCommentId desc
        """)
    List<PostComment> findFirstCommentPage(@Param("post") Post post, Pageable pageable);

    // 커서 이후(더 오래된) 댓글 페이지
    @Query("""
        select c from PostComment c
        join fetch c.user
        where c.post = :post
        and (
            c.createdAt < :cursorCreatedAt
            or (c.createdAt = :cursorCreatedAt and c.postCommentId < :cursorId)
        )
        order by c.createdAt desc, c.postCommentId desc
        """)
    List<PostComment> findCommentPageAfterCursor(
        @Param("post") Post post,
        @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
        @Param("cursorId") Long cursorId,
        Pageable pageable
    );

}
