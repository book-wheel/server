package com.bookwheel.server.schedule.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record GroupScheduleCreateRequest(
        @NotNull(message = "시작 일을 입력해주세요.")
        LocalDate startDate
) {
}
