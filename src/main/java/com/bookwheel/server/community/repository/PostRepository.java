package com.bookwheel.server.community.repository;

import com.bookwheel.server.community.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {
}
