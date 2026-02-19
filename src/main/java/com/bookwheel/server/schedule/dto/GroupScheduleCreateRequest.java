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

        @Schema(description = "일정 종료일(선택). 전달 시 계산된 마지막 회차 종료일과 일치해야 합니다.", example = "2026-03-31")
        LocalDate endDate,

        @Schema(description = "독서일 계산에서 제외할 날짜 목록(선택)", example = "[\"2026-03-03\", \"2026-03-09\"]")
        List<LocalDate> excludedDates
) {
}
