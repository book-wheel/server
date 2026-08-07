package com.bookwheel.server.community.dto;

import com.bookwheel.server.community.entity.BookReview;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Representative public review for a recommended book")
public record BookExchangeRecommendationReview(
    @Schema(description = "Review ID", example = "1")
    Long reviewId,

    @Schema(description = "Reviewer nickname", example = "문희연")
    String reviewerName,

    @Schema(description = "Public review comment")
    String comment,

    @Schema(description = "Review like count", example = "5")
    int likeCount,

    @Schema(description = "Review created date time")
    LocalDateTime createdAt
) {
    public static BookExchangeRecommendationReview from(BookReview review) {
        return new BookExchangeRecommendationReview(
            review.getReviewId(),
            review.getReviewer().getNickname(),
            review.getContent(),
            review.getLikeCount(),
            review.getCreatedAt()
        );
    }
}
