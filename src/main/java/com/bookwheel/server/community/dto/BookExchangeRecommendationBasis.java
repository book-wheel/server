package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Book exchange recommendation source and selection basis")
public record BookExchangeRecommendationBasis(
    @Schema(description = "Recommendation selection type", example = "DAILY_ROTATION")
    String type,

    @Schema(description = "Recommendation data source code", example = "DATA4LIBRARY")
    String source,

    @Schema(description = "Recommendation data source name", example = "도서관 정보나루")
    String sourceName,

    @Schema(description = "Recommendation data provider", example = "국립중앙도서관")
    String provider,

    @Schema(description = "Data source guide URL", example = "https://www.data4library.kr/apiUtilization")
    String sourceUrl,

    @Schema(description = "Source aggregation start date", nullable = true)
    LocalDate startDate,

    @Schema(description = "Source aggregation end date", nullable = true)
    LocalDate endDate,

    @Schema(description = "Human-readable recommendation basis")
    String description
) {
}
