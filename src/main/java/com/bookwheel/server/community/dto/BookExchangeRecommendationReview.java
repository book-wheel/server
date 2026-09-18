package com.bookwheel.server.community.dto;

import com.bookwheel.server.community.entity.BookReview;
import com.bookwheel.server.community.support.CommunityAuthorDisplay;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "추천 도서의 대표 공개 후기")
public record BookExchangeRecommendationReview(
    @Schema(description = "후기 ID", example = "1")
    Long reviewId,

    @Schema(description = "후기 작성자 닉네임 (탈퇴 후 '탈퇴한 사용자')", example = "문희연")
    String reviewerName,

    @Schema(description = "공개 후기 내용")
    String comment,

    @Schema(description = "후기 공감 수", example = "5")
    int likeCount,

    @Schema(description = "후기 작성 일시")
    LocalDateTime createdAt
) {
    public static BookExchangeRecommendationReview from(BookReview review) {
        return new BookExchangeRecommendationReview(
            review.getReviewId(),
            CommunityAuthorDisplay.displayName(review.getReviewer()),
            review.getContent(),
            review.getLikeCount(),
            review.getCreatedAt()
        );
    }
}
