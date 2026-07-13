package com.bookwheel.server.community.repository;

import com.bookwheel.server.community.entity.BookReview;
import com.bookwheel.server.community.entity.ReviewLike;
import com.bookwheel.server.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewLikeRepository extends JpaRepository<ReviewLike, Long> {
    Optional<ReviewLike> findByReviewAndUser(BookReview review, User user);
    boolean existsByReviewAndUser(BookReview review, User user);

    // 주어진 리뷰 목록 중 해당 사용자가 공감한 리뷰 ID만 한 번에 조회한다. (리뷰별 exists N+1 방지)
    @Query("select rl.review.reviewId from ReviewLike rl where rl.user = :user and rl.review.reviewId in :reviewIds")
    List<Long> findLikedReviewIds(@Param("user") User user, @Param("reviewIds") List<Long> reviewIds);
}
