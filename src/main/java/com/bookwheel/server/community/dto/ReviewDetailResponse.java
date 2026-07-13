package com.bookwheel.server.community.dto;

import com.bookwheel.server.community.entity.BookReview;

import java.time.LocalDateTime;

public record ReviewDetailResponse(
    Long reviewId,
    String isbn,
    String reviewerName,
    String profileImageUrl,
    boolean isRecommended,
    String comment,
    boolean isHidden,
    int likeCount,
    boolean isLikedByMe,
    LocalDateTime createdAt
) {
    public static ReviewDetailResponse of(BookReview review, String profileImageUrl, boolean isLikedByMe) {
        return new ReviewDetailResponse(
            review.getReviewId(),
            review.getBookInfo().getIsbn(),
            review.getReviewer().getNickname(),
            profileImageUrl,
            review.getIsRecommended(),
            review.getContent(),
            review.getIsHidden(),
            review.getLikeCount(),
            isLikedByMe,
            review.getCreatedAt()
        );
    }
}
