package com.bookwheel.server.community.repository;

import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.bookwheel.server.user.entity.User;

import java.util.Optional;
import java.util.Collection;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {


    Optional<PostLike> findByPostAndUser(Post post, User user);

    boolean existsByPostAndUser(Post post, User user);

    @Modifying
    @Query("delete from PostLike postLike where postLike.post.postId in :postIds")
    // 게시물 삭제 전에 좋아요가 참조하는 게시물을 먼저 제거한다.
    void deleteAllByPostIds(@Param("postIds") Collection<Long> postIds);
}
