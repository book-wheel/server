package com.bookwheel.server.community.repository;

import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.bookwheel.server.user.entity.User;

import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {


    Optional<PostLike> findByPostAndUser(Post post, User user);

    boolean existsByPostAndUser(Post post, User user);

    @Modifying(flushAutomatically = true)
    @Query("delete from PostLike l where l.post = :post")
    // 게시물 삭제 전에 해당 게시물의 좋아요만 일괄 제거한다. (파생 삭제의 건별 DELETE를 피한다)
    void deleteAllByPost(@Param("post") Post post);
}
