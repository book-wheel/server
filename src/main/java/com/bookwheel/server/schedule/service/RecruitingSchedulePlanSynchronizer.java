package com.bookwheel.server.schedule.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.schedule.dto.ExcludedDateRange;
import com.bookwheel.server.schedule.entity.Round;
import com.bookwheel.server.schedule.repository.RoundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecruitingSchedulePlanSynchronizer {
    private final RoundRepository roundRepository;
    private final ScheduleCalendarService scheduleCalendarService;

    public boolean synchronizeToMaxMembers(Group group) {
        if (group.getGroupState() != State.RECRUITING) {
            return false;
        }

        List<Round> existingRounds = roundRepository
                .findByGroup_GroupIdOrderByRoundNumberAsc(group.getGroupId());
        if (existingRounds.isEmpty()) {
            return false;
        }

        int targetMemberCount = validateTargetMemberCount(group.getMaxMembers());
        int expectedRoundCount = targetMemberCount - 1;
        validateExistingSchedule(group, existingRounds, expectedRoundCount);

        boolean changed = !Objects.equals(group.getTargetMemberCount(), targetMemberCount)
                || group.getGroupRoundCount() != expectedRoundCount
                || existingRounds.size() != expectedRoundCount;
        if (!changed) {
            return false;
        }

        validateScheduleCapacity(group, expectedRoundCount);
        List<Round> addedRounds = appendMissingRounds(group, existingRounds, expectedRoundCount);
        validateFinalEndDate(group, existingRounds, addedRounds);
        group.updateSchedulePlan(group.getStartDate(), expectedRoundCount, targetMemberCount);
        if (!addedRounds.isEmpty()) {
            roundRepository.saveAll(addedRounds);
        }
        return true;
    }

    private int validateTargetMemberCount(Integer maxMembers) {
        if (maxMembers == null || maxMembers < 2 || maxMembers > Group.MAX_MEMBER_COUNT) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_TARGET_MEMBER_INVALID);
        }
        return maxMembers;
    }

    private void validateExistingSchedule(
            Group group,
            List<Round> rounds,
            int expectedRoundCount
    ) {
        if (group.getStartDate() == null
                || group.getReadingPeriod() == null
                || group.getReadingPeriod() < 1
                || rounds.size() > expectedRoundCount) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_TARGET_MEMBER_INVALID);
        }

        LocalDate previousEndDate = null;
        for (int index = 0; index < rounds.size(); index++) {
            Round round = rounds.get(index);
            LocalDate expectedStartDate = index == 0
                    ? group.getStartDate()
                    : previousEndDate.plusDays(1);
            if (!Objects.equals(round.getRoundNumber(), index + 1)
                    || round.getStartDate() == null
                    || round.getEndDate() == null
                    || round.getStartDate().isAfter(round.getEndDate())
                    || !round.getStartDate().equals(expectedStartDate)) {
                throw new BusinessException(ErrorCode.GROUP_SCHEDULE_TARGET_MEMBER_INVALID);
            }
            previousEndDate = round.getEndDate();
        }
    }

    private void validateScheduleCapacity(Group group, int expectedRoundCount) {
        ScheduleCalendarService.ExcludedCalendar excludedCalendar = excludedCalendar(group);
        LocalDate deadline = SchedulePolicy.resolveCalculationDeadline(
                group.getStartDate(),
                group.getScheduleEndDate()
        );
        long requiredUsableDays = (long) expectedRoundCount * group.getReadingPeriod();
        long usableDays = scheduleCalendarService.countUsableDaysUntilDeadline(
                group.getStartDate(),
                deadline,
                excludedCalendar
        );
        if (usableDays < requiredUsableDays) {
            ErrorCode errorCode = group.getScheduleEndDate() == null
                    ? ErrorCode.GROUP_SCHEDULE_DURATION_EXCEEDED
                    : ErrorCode.GROUP_SCHEDULE_END_DATE_MISMATCH;
            throw new BusinessException(errorCode);
        }
    }

    private List<Round> appendMissingRounds(
            Group group,
            List<Round> existingRounds,
            int expectedRoundCount
    ) {
        if (existingRounds.size() == expectedRoundCount) {
            return List.of();
        }

        ScheduleCalendarService.ExcludedCalendar excludedCalendar = excludedCalendar(group);
        LocalDate nextStartDate = existingRounds.get(existingRounds.size() - 1)
                .getEndDate()
                .plusDays(1);
        List<Round> addedRounds = new ArrayList<>(expectedRoundCount - existingRounds.size());
        for (int roundNumber = existingRounds.size() + 1;
             roundNumber <= expectedRoundCount;
             roundNumber++) {
            LocalDate endDate = scheduleCalendarService.calculateRoundEndDate(
                    nextStartDate,
                    group.getReadingPeriod(),
                    excludedCalendar
            );
            addedRounds.add(Round.builder()
                    .roundId(UUID.randomUUID().toString())
                    .group(group)
                    .roundNumber(roundNumber)
                    .startDate(nextStartDate)
                    .endDate(endDate)
                    .build());
            nextStartDate = endDate.plusDays(1);
        }
        return List.copyOf(addedRounds);
    }

    private void validateFinalEndDate(
            Group group,
            List<Round> existingRounds,
            List<Round> addedRounds
    ) {
        LocalDate finalEndDate = addedRounds.isEmpty()
                ? existingRounds.get(existingRounds.size() - 1).getEndDate()
                : addedRounds.get(addedRounds.size() - 1).getEndDate();
        LocalDate deadline = SchedulePolicy.resolveCalculationDeadline(
                group.getStartDate(),
                group.getScheduleEndDate()
        );
        if (finalEndDate.isAfter(deadline)) {
            ErrorCode errorCode = group.getScheduleEndDate() == null
                    ? ErrorCode.GROUP_SCHEDULE_DURATION_EXCEEDED
                    : ErrorCode.GROUP_SCHEDULE_END_DATE_MISMATCH;
            throw new BusinessException(errorCode);
        }
    }

    private ScheduleCalendarService.ExcludedCalendar excludedCalendar(Group group) {
        return scheduleCalendarService.normalizeExcludedCalendar(
                deserializeExcludedDates(group.getScheduleExcludedDates()),
                deserializeExcludedDateRanges(group.getScheduleExcludedDateRanges())
        );
    }

    private List<LocalDate> deserializeExcludedDates(String serializedDates) {
        if (serializedDates == null || serializedDates.isBlank()) {
            return List.of();
        }
        return Arrays.stream(serializedDates.split(","))
                .map(LocalDate::parse)
                .toList();
    }

    private List<ExcludedDateRange> deserializeExcludedDateRanges(String serializedRanges) {
        if (serializedRanges == null || serializedRanges.isBlank()) {
            return List.of();
        }
        return Arrays.stream(serializedRanges.split(","))
                .map(serializedRange -> serializedRange.split(":"))
                .map(parts -> new ExcludedDateRange(
                        LocalDate.parse(parts[0]),
                        LocalDate.parse(parts[1])
                ))
                .toList();
    }
}
