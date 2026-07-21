package com.bookwheel.server.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;

@Builder
@Schema(description = "생성된 라운드 일정. 멤버가 1명이면 일정 설정만 저장되고 빈 목록을 반환합니다.")
public record GroupScheduleRoundResponse(
        @Schema(description = "라운드 번호", example = "1")
        int roundNumber,

        @Schema(description = "라운드 시작일", example = "2026-06-25")
        LocalDate startDate,

        @Schema(description = "라운드 종료일", example = "2026-06-27")
        LocalDate endDate
) {
    public static GroupScheduleRoundResponse of(int roundNumber, LocalDate startDate, LocalDate endDate) {
        return GroupScheduleRoundResponse.builder()
                .roundNumber(roundNumber)
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }
}
