package com.bookwheel.server.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Excluded date range (inclusive start/end)")
public record ExcludedDateRange(
        @Schema(description = "Range start date (inclusive)", example = "2026-03-01")
        LocalDate startDate,

        @Schema(description = "Range end date (inclusive)", example = "2026-03-03")
        LocalDate endDate
) {
}
