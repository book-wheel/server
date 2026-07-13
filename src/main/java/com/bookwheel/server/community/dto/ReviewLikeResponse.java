package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "리뷰 공감 상태 변경 결과")
public record ReviewLikeResponse(
    @Schema(description = "리뷰 ID", example = "1")
    Long reviewId,

    @Schema(description = "변경 후 내 공감 여부", example = "true")
    boolean isLikedByMe,

    @Schema(description = "변경 후 공감 수", example = "27")
    int likeCount
) {
    public static ReviewLikeResponse of(Long reviewId, boolean isLikedByMe, int likeCount) {
        return new ReviewLikeResponse(reviewId, isLikedByMe, likeCount);
    }
}