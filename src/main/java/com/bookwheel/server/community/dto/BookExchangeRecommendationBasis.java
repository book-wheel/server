package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "교환독서 추천도서의 출처 및 선정 기준 정보")
public record BookExchangeRecommendationBasis(
    @Schema(description = "추천 선정 방식", example = "DAILY_ROTATION")
    String type,

    @Schema(description = "추천 데이터 출처 코드", example = "DATA4LIBRARY")
    String source,

    @Schema(description = "추천 데이터 출처명", example = "도서관 정보나루")
    String sourceName,

    @Schema(description = "추천 데이터 제공 기관", example = "국립중앙도서관")
    String provider,

    @Schema(description = "출처 데이터 안내 URL", example = "https://www.data4library.kr/apiUtilization")
    String sourceUrl,

    @Schema(description = "출처 데이터 집계 시작일. 적재된 스냅샷이 없으면 null입니다.", nullable = true)
    LocalDate startDate,

    @Schema(description = "출처 데이터 집계 종료일. 적재된 스냅샷이 없으면 null입니다.", nullable = true)
    LocalDate endDate,

    @Schema(description = "사용자에게 노출할 수 있는 추천 기준 설명 문구")
    String description
) {
}
