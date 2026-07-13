package com.bookwheel.server.schedule.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.schedule.dto.ExcludedDateRange;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
class ScheduleCalendarService {
    ExcludedCalendar emptyCalendar() {
        return new ExcludedCalendar(List.of());
    }

    LocalDate calculateRoundEndDate(
            LocalDate currentStart,
            int readingPeriod,
            ExcludedCalendar excludedCalendar
    ) {
        LocalDate cursor = currentStart;
        int validDayCount = 0;

        while (validDayCount < readingPeriod) {
            if (!excludedCalendar.isExcluded(cursor)) {
                validDayCount++;
            }
            if (validDayCount == readingPeriod) {
                return cursor;
            }
            cursor = cursor.plusDays(1);
        }

        return cursor;
    }

    ExcludedCalendar normalizeExcludedCalendar(
            List<LocalDate> excludedDates,
            List<ExcludedDateRange> excludedDateRanges
    ) {
        List<DateRange> ranges = new ArrayList<>();
        if (excludedDates != null) {
            for (LocalDate excludedDate : excludedDates) {
                if (excludedDate == null) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
                }
                ranges.add(new DateRange(excludedDate, excludedDate));
            }
        }

        if (excludedDateRanges != null) {
            for (ExcludedDateRange range : excludedDateRanges) {
                validateExcludedDateRange(range);
                ranges.add(new DateRange(range.startDate(), range.endDate()));
            }
        }

        return new ExcludedCalendar(mergeRanges(ranges));
    }

    long countUsableDaysUntilDeadline(
            LocalDate startDate,
            LocalDate requestedEndDate,
            ExcludedCalendar excludedCalendar
    ) {
        return excludedCalendar.countUsableDaysInInterval(startDate, requestedEndDate);
    }

    private void validateExcludedDateRange(ExcludedDateRange range) {
        if (range == null || range.startDate() == null || range.endDate() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        if (range.endDate().isBefore(range.startDate())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private List<DateRange> mergeRanges(List<DateRange> ranges) {
        if (ranges.isEmpty()) {
            return List.of();
        }

        List<DateRange> sorted = ranges.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(DateRange::startDate).thenComparing(DateRange::endDate))
                .toList();

        List<DateRange> merged = new ArrayList<>();
        DateRange current = sorted.get(0);

        for (int i = 1; i < sorted.size(); i++) {
            DateRange next = sorted.get(i);
            boolean overlappedOrAdjacent = !next.startDate().isAfter(current.endDate().plusDays(1));

            if (overlappedOrAdjacent) {
                LocalDate mergedEnd = current.endDate().isAfter(next.endDate()) ? current.endDate() : next.endDate();
                current = new DateRange(current.startDate(), mergedEnd);
                continue;
            }

            merged.add(current);
            current = next;
        }

        merged.add(current);
        return merged;
    }

    static final class ExcludedCalendar {
        private final List<DateRange> mergedExcludedRanges;

        private ExcludedCalendar(List<DateRange> mergedExcludedRanges) {
            this.mergedExcludedRanges = List.copyOf(mergedExcludedRanges);
        }

        private boolean isExcluded(LocalDate date) {
            int left = 0;
            int right = mergedExcludedRanges.size() - 1;

            while (left <= right) {
                int mid = (left + right) >>> 1;
                DateRange range = mergedExcludedRanges.get(mid);

                if (date.isBefore(range.startDate())) {
                    right = mid - 1;
                    continue;
                }

                if (date.isAfter(range.endDate())) {
                    left = mid + 1;
                    continue;
                }

                if (range.contains(date)) {
                    return true;
                }
            }

            return false;
        }

        private long countUsableDaysInInterval(LocalDate startDate, LocalDate endDate) {
            long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1L;
            return totalDays - countExcludedDaysInInterval(startDate, endDate);
        }

        private long countExcludedDaysInInterval(LocalDate startDate, LocalDate endDate) {
            long excludedDays = 0L;

            for (DateRange range : mergedExcludedRanges) {
                if (range.endDate().isBefore(startDate)) {
                    continue;
                }
                if (range.startDate().isAfter(endDate)) {
                    break;
                }

                LocalDate overlapStart = range.startDate().isAfter(startDate) ? range.startDate() : startDate;
                LocalDate overlapEnd = range.endDate().isBefore(endDate) ? range.endDate() : endDate;
                excludedDays += ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1L;
            }

            return excludedDays;
        }
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
        private boolean contains(LocalDate date) {
            return !date.isBefore(startDate) && !date.isAfter(endDate);
        }
    }
}
