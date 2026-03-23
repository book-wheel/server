package com.bookwheel.server.community.repository;

import com.bookwheel.server.community.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("SELECT p FROM Post p " +
        "JOIN FETCH p.uploader " +
        "JOIN FETCH p.bookInfo")
    List<Post> findAllWithDetails();
}
