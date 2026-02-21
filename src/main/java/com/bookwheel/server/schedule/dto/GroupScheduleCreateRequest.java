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

        @Schema(description = "일정 마감일 (선택 사항). 계산된 최종 종료일은 이 날짜보다 작거나 같아야 합니다.", example = "2026-03-31")
        LocalDate endDate,

        @Schema(description = "제외할 개별 날짜 목록 (선택 사항)", example = "[\"2026-03-03\", \"2026-03-09\"]")
        List<LocalDate> excludedDates,

        @Schema(
                description = "제외할 날짜 범위 목록 (선택 사항, 시작 및 종료일 포함).",
                example = "[{\"startDate\":\"2026-03-10\",\"endDate\":\"2026-03-12\"},{\"startDate\":\"2026-03-20\",\"endDate\":\"2026-03-22\"}]"
        )
        List<ExcludedDateRange> excludedDateRanges
) {
}