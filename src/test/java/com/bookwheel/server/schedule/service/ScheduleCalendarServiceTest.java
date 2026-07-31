package com.bookwheel.server.schedule.service;

import com.bookwheel.server.schedule.dto.ExcludedDateRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleCalendarServiceTest {

    private final ScheduleCalendarService scheduleCalendarService = new ScheduleCalendarService();

    @Test
    @DisplayName("제외 날짜 범위가 LocalDate 최대값까지 이어져도 오버플로 없이 병합한다")
    void normalizeExcludedCalendar_MergesRangeEndingAtLocalDateMax() {
        LocalDate intervalStart = LocalDate.MAX.minusDays(2);
        ScheduleCalendarService.ExcludedCalendar excludedCalendar =
                scheduleCalendarService.normalizeExcludedCalendar(
                        List.of(LocalDate.MAX),
                        List.of(new ExcludedDateRange(LocalDate.MAX.minusDays(1), LocalDate.MAX))
                );

        long usableDays = scheduleCalendarService.countUsableDaysUntilDeadline(
                intervalStart,
                LocalDate.MAX,
                excludedCalendar
        );

        assertThat(usableDays).isEqualTo(1L);
    }
}
