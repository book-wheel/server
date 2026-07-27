package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "추천/비추천 투표 결과 및 통계")
public record ReviewVoteResponse(
    @Schema(description = "도서 ISBN", example = "9791161571188")
    String isbn,

    @Schema(description = "추천 비율 (%)", example = "80")
    int recommendedRatio,

    @Schema(description = "비추천 비율 (%)", example = "20")
    int notRecommendedRatio,

    @Schema(description = "로그인 사용자의 선택값 (RECOMMEND / NOT_RECOMMEND / null)", nullable = true)
    VoteType myVote
) {}
