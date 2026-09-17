package com.bookwheel.server.schedule.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.schedule.entity.Round;
import com.bookwheel.server.schedule.repository.RoundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
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

        int targetMemberCount = group.getMaxMembers() == null ? 0 : group.getMaxMembers();
        int expectedRoundCount = targetMemberCount - 1;
        if (targetMemberCount < 2
                || targetMemberCount > Group.MAX_MEMBER_COUNT
                || group.getStartDate() == null
                || group.getReadingPeriod() == null
                || group.getReadingPeriod() < 1
                || existingRounds.size() > expectedRoundCount) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_TARGET_MEMBER_INVALID);
        }

        boolean changed = !Objects.equals(group.getTargetMemberCount(), targetMemberCount)
                || group.getGroupRoundCount() != expectedRoundCount
                || existingRounds.size() != expectedRoundCount;
        if (!changed) {
            return false;
        }

        List<Round> addedRounds = appendMissingRounds(group, existingRounds, expectedRoundCount);
        group.updateSchedulePlan(group.getStartDate(), expectedRoundCount, targetMemberCount);
        if (!addedRounds.isEmpty()) {
            roundRepository.saveAll(addedRounds);
        }
        return true;
    }

    private List<Round> appendMissingRounds(
            Group group,
            List<Round> existingRounds,
            int expectedRoundCount
    ) {
        if (existingRounds.size() == expectedRoundCount) {
            return List.of();
        }

        Round lastRound = existingRounds.get(existingRounds.size() - 1);
        if (!Objects.equals(lastRound.getRoundNumber(), existingRounds.size())
                || lastRound.getEndDate() == null) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_TARGET_MEMBER_INVALID);
        }

        ScheduleCalendarService.ExcludedCalendar excludedCalendar = excludedCalendar(group);
        LocalDate nextStartDate = lastRound.getEndDate().plusDays(1);
        LocalDate deadline = SchedulePolicy.resolveCalculationDeadline(
                group.getStartDate(),
                group.getScheduleEndDate()
        );
        long requiredUsableDays = (long) (expectedRoundCount - existingRounds.size())
                * group.getReadingPeriod();
        if (nextStartDate.isAfter(deadline)
                || scheduleCalendarService.countUsableDaysUntilDeadline(
                        nextStartDate,
                        deadline,
                        excludedCalendar
                ) < requiredUsableDays) {
            throw scheduleCapacityException(group);
        }

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

    private BusinessException scheduleCapacityException(Group group) {
        ErrorCode errorCode = group.getScheduleEndDate() == null
                ? ErrorCode.GROUP_SCHEDULE_DURATION_EXCEEDED
                : ErrorCode.GROUP_SCHEDULE_END_DATE_MISMATCH;
        return new BusinessException(errorCode);
    }

    private ScheduleCalendarService.ExcludedCalendar excludedCalendar(Group group) {
        return scheduleCalendarService.deserializeExcludedCalendar(
                group.getScheduleExcludedDates(),
                group.getScheduleExcludedDateRanges()
        );
    }
}
