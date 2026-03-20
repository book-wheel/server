package com.bookwheel.server.community.repository;

import com.bookwheel.server.community.entity.BookReview;
import com.bookwheel.server.community.entity.ReviewLike;
import com.bookwheel.server.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewLikeRepository extends JpaRepository<ReviewLike, Long> {
    Optional<ReviewLike> findByReviewAndUser(BookReview review, User user);
    boolean existsByReviewAndUser(BookReview review, User user);
}
