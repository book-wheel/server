package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Daily book exchange recommendation response")
public record BookExchangeRecommendationResponse(
    @Schema(description = "Recommendation date in Asia/Seoul", example = "2026-08-08")
    LocalDate recommendationDate,

    @Schema(description = "Recommendation source and selection basis")
    BookExchangeRecommendationBasis basis,

    @Schema(description = "Recommended book. Null when there is no available source snapshot.", nullable = true)
    BookExchangeRecommendationBook book
) {
}
