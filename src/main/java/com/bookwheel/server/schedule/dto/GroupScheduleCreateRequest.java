package com.bookwheel.server.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "그룹 독서 일정 생성 요청")
public record GroupScheduleCreateRequest(
        @Schema(description = "일정 시작일", example = "2026-02-01")
        @NotNull(message = "시작 일을 입력해주세요.")
        LocalDate startDate
) {
}
