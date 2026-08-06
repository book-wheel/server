package com.bookwheel.server.community.repository;

import com.bookwheel.server.community.entity.Post;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long>, PostRepositoryCustom {

    @Query("SELECT p FROM Post p " +
        "JOIN FETCH p.uploader " +
        "JOIN FETCH p.bookInfo")
    List<Post> findAllWithDetails();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Post p where p.postId = :postId")
    // 게시물 삭제와 그 게시물을 가리키는 비동기 알림 저장을 같은 게시물 단위로 직렬화한다.
    Optional<Post> findByPostIdForUpdate(@Param("postId") Long postId);
}
