package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "추천/비추천 투표 요청 DTO")
public record ReviewVoteRequest(
    @Schema(description = "추천/비추천 선택값 (RECOMMEND: 추천, NOT_RECOMMEND: 비추천)", example = "RECOMMEND")
    @NotNull(message = "추천/비추천 값은 필수입니다.")
    VoteType vote
) {
    // RECOMMEND -> true, NOT_RECOMMEND -> false
    public boolean isRecommended() {
        return this.vote == VoteType.RECOMMEND;
    }
}
