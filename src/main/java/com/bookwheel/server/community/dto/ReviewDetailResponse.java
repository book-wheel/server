package com.bookwheel.server.community.dto;

import com.bookwheel.server.community.entity.BookReview;

import java.time.LocalDateTime;

public record ReviewDetailResponse(
    Long reviewId,
    String bookId,
    String reviewerName,
    boolean isRecommended,
    String comment,
    boolean isHidden,
    int likeCount,
    boolean isLikedByMe,
    LocalDateTime createdAt
) {
    public static ReviewDetailResponse of(BookReview review, boolean isLikedByMe) {
        return new ReviewDetailResponse(
            review.getReviewId(),
            review.getBook().getBookId(),
            review.getReviewer().getNickname(),
            review.getIsRecommended(),
            review.getContent(),
            review.getIsHidden(),
            review.getLikeCount(),
            isLikedByMe,
            review.getCreatedAt()
        );
    }
}
