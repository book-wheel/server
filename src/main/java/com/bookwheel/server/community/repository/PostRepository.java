package com.bookwheel.server.community.repository;

import com.bookwheel.server.community.entity.Post;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long>, PostRepositoryCustom {

    @EntityGraph(attributePaths = "images")
    // 게시물 이미지 키를 수집할 수 있도록 이미지와 함께 그룹 게시물을 조회한다.
    List<Post> findByGroup_GroupId(String groupId);

    @Query("SELECT p FROM Post p " +
        "JOIN FETCH p.uploader " +
        "JOIN FETCH p.bookInfo")
    List<Post> findAllWithDetails();
}
