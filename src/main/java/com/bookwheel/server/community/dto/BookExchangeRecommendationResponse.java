package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "오늘의 교환독서 추천도서 응답")
public record BookExchangeRecommendationResponse(
    @Schema(description = "추천 기준 날짜 (Asia/Seoul)", example = "2026-08-08")
    LocalDate recommendationDate,

    @Schema(description = "추천 출처 및 선정 기준 정보")
    BookExchangeRecommendationBasis basis,

    @Schema(description = "추천 도서. 사용할 수 있는 출처 스냅샷이 없으면 null입니다.", nullable = true)
    BookExchangeRecommendationBook book
) {
}