package com.bookwheel.server.schedule.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;

import java.time.DateTimeException;
import java.time.LocalDate;

final class SchedulePolicy {
    static final int MAX_SCHEDULE_YEARS = 3;

    private SchedulePolicy() {
    }

    // endDate가 없어도 일정 계산이 무한히 확장되지 않도록 서버 내부 종료 한계를 적용한다.
    static LocalDate resolveCalculationDeadline(LocalDate startDate, LocalDate requestedEndDate) {
        LocalDate maximumEndDate;
        try {
            maximumEndDate = startDate.plusYears(MAX_SCHEDULE_YEARS);
        } catch (DateTimeException exception) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_DURATION_EXCEEDED);
        }

        if (requestedEndDate != null && requestedEndDate.isAfter(maximumEndDate)) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_DURATION_EXCEEDED);
        }
        return requestedEndDate == null ? maximumEndDate : requestedEndDate;
    }
}
