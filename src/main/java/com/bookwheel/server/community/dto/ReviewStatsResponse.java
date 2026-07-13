package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "리뷰 추천/비추천 통계 정보")
public record ReviewStatsResponse(
    @Schema(description = "추천 비율 (%)", example = "80")
    int recommendedRatio,   // 추천 비율 (%)

    @Schema(description = "비추천 비율 (%)", example = "20")
    int notRecommendedRatio, // 비추천 비율 (%)

    @Schema(description = "로그인 사용자의 선택값 (RECOMMEND / NOT_RECOMMEND / null)", nullable = true)
    VoteType myVote
) {}