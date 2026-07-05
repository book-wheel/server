package com.bookwheel.server.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "그룹 독서 일정 생성 요청")
public record GroupScheduleCreateRequest(
        @Schema(description = "일정 시작일", example = "2026-02-01")
        @NotNull(message = "시작 일을 입력해주세요.")
        LocalDate startDate,

        @Schema(description = "최대 종료일 제한. 선택값이며 없으면 제한 없이 계산합니다.", example = "2026-07-31", nullable = true)
        LocalDate endDate,

        @Schema(description = "제외할 개별 날짜 목록", example = "[\"2026-06-28\", \"2026-07-01\"]", nullable = true)
        List<LocalDate> excludedDates,

        @Schema(description = "제외할 날짜 범위 목록. 시작일과 종료일을 포함합니다.", nullable = true)
        List<ExcludedDateRange> excludedDateRanges
) {
}