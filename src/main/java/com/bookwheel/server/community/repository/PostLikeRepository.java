package com.bookwheel.server.community.repository;

import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import com.bookwheel.server.user.entity.User;

import java.util.Optional;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {


    Optional<PostLike> findByPostAndUser(Post post, User user);

    boolean existsByPostAndUser(Post post, User user);
}
