package com.bookwheel.server.schedule.dto;

import lombok.Builder;

import java.time.LocalDate;

@Builder
public record GroupScheduleRoundResponse(
        int roundNumber,
        LocalDate startDate,
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
