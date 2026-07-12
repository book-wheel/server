package com.bookwheel.server.schedule.service;

import com.bookwheel.server.book.entity.OwnBook;
import com.bookwheel.server.book.repository.OwnBookRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.group.service.GroupMemberPermissionValidator;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.schedule.dto.GroupScheduleFutureRequest;
import com.bookwheel.server.schedule.dto.GroupScheduleRoundResponse;
import com.bookwheel.server.schedule.entity.Round;
import com.bookwheel.server.schedule.repository.RoundRepository;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import com.bookwheel.server.wheel.dto.WheelAssignmentPlan;
import com.bookwheel.server.wheel.entity.WheelState;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import com.bookwheel.server.wheel.service.WheelReassignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FutureScheduleService {
    private final GroupRepository groupRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final OwnBookRepository ownBookRepository;
    private final RoundRepository roundRepository;
    private final WheelStateRepository wheelStateRepository;
    private final GroupMemberPermissionValidator memberPermissionValidator;
    private final WheelReassignmentService wheelReassignmentService;
    private final ScheduleCalendarService scheduleCalendarService;
    private final Clock clock;

    @Transactional
    public List<GroupScheduleRoundResponse> regenerateFutureSchedule(
            String groupId,
            GroupScheduleFutureRequest request,
            String userPK
    ) {
        Group group = findGroupByIdForUpdate(groupId);
        findActiveUserById(userPK);
        memberPermissionValidator.validateLeader(groupId, userPK);
        validateFutureScheduleState(group.getGroupState());

        if (request == null || request.totalRoundCount() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Integer readingPeriod = group.getReadingPeriod();
        if (readingPeriod == null || readingPeriod < 1) {
            throw new BusinessException(ErrorCode.GROUP_READING_PERIOD_INVALID);
        }

        LocalDate today = LocalDate.now(clock);
        List<Member> activeMembers = memberRepository.findByGroup_GroupIdAndMemberStatus(groupId, MemberStatus.ACTIVE);
        List<Round> existingRounds = roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId);
        // 오늘 시작했거나 이미 끝난 라운드는 독서 기록과 연결되므로 미래 일정 재생성 대상에서 제외한다.
        List<Round> protectedRounds = protectedRounds(existingRounds, today);
        List<Round> futureRounds = futureRounds(existingRounds, today);

        int totalRoundCount = request.totalRoundCount();
        int protectedRoundCount = protectedRounds.size();
        if (totalRoundCount < protectedRoundCount) {
            throw new BusinessException(ErrorCode.GROUP_FUTURE_SCHEDULE_TOTAL_BELOW_PROTECTED);
        }

        int maxTotalRoundCount = protectedRoundCount + activeMembers.size() - 1;
        if (totalRoundCount > maxTotalRoundCount) {
            throw new BusinessException(ErrorCode.GROUP_FUTURE_SCHEDULE_TOTAL_EXCEEDS_ACTIVE_LIMIT);
        }

        int futureRoundCount = totalRoundCount - protectedRoundCount;
        ScheduleCalendarService.ExcludedCalendar excludedCalendar = scheduleCalendarService.normalizeExcludedCalendar(
                request.excludedDates(),
                request.excludedDateRanges()
        );
        LocalDate firstFutureStartDate = firstFutureStartDate(group, protectedRounds);
        validateFutureEndDate(firstFutureStartDate, request.endDate(), futureRoundCount, readingPeriod, excludedCalendar);

        List<Round> newFutureRounds = createFutureRounds(
                group, protectedRoundCount + 1, firstFutureStartDate, futureRoundCount, readingPeriod, excludedCalendar
        );

        WheelAssignmentPlan futureAssignmentPlan = WheelAssignmentPlan.empty();
        List<OwnBook> books = List.of();
        if (!newFutureRounds.isEmpty()) {
            List<String> protectedRoundIds = protectedRounds.stream()
                    .map(Round::getRoundId)
                    .toList();
            List<WheelState> protectedStates = protectedRoundIds.isEmpty()
                    ? List.of()
                    : wheelStateRepository.findByRoundIdIn(protectedRoundIds);
            books = ownBookRepository.findByGroup_GroupIdIn(List.of(groupId));
            futureAssignmentPlan = wheelReassignmentService.planFutureAssignments(
                    newFutureRounds,
                    activeMembers,
                    books,
                    protectedStates
            );
        }

        List<String> futureRoundIds = futureRounds.stream()
                .map(Round::getRoundId)
                .toList();
        if (!futureRoundIds.isEmpty()) {
            // 새 배정 전체가 가능한지 먼저 검증한 뒤, 미래 라운드와 PLANNED 배정만 교체한다.
            wheelStateRepository.deleteByRoundIdIn(futureRoundIds);
            roundRepository.deleteByRoundIdIn(futureRoundIds);
            roundRepository.flush();
        }
        if (!newFutureRounds.isEmpty()) {
            roundRepository.saveAll(newFutureRounds);
            wheelReassignmentService.savePlannedAssignments(futureAssignmentPlan, activeMembers, books);
        }
        group.updateScheduleInfo(group.getStartDate(), totalRoundCount);

        return response(protectedRounds, newFutureRounds, totalRoundCount);
    }

    private void validateFutureScheduleState(State state) {
        if (state == State.RECRUITING) {
            throw new BusinessException(ErrorCode.GROUP_FUTURE_SCHEDULE_RECRUITING_STATE_INVALID);
        }
        if (state == State.COMPLETE) {
            throw new BusinessException(ErrorCode.GROUP_FUTURE_SCHEDULE_COMPLETE_STATE_INVALID);
        }
        if (state != State.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private List<Round> protectedRounds(List<Round> rounds, LocalDate today) {
        return rounds.stream()
                .filter(round -> round.getStartDate() != null && !round.getStartDate().isAfter(today))
                .sorted(Comparator.comparing(Round::getRoundNumber))
                .toList();
    }

    private List<Round> futureRounds(List<Round> rounds, LocalDate today) {
        return rounds.stream()
                .filter(round -> round.getStartDate() != null && round.getStartDate().isAfter(today))
                .sorted(Comparator.comparing(Round::getRoundNumber))
                .toList();
    }

    private LocalDate firstFutureStartDate(Group group, List<Round> protectedRounds) {
        if (protectedRounds.isEmpty()) {
            LocalDate groupStartDate = group.getStartDate();
            if (groupStartDate == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            return groupStartDate;
        }

        Round lastProtectedRound = protectedRounds.get(protectedRounds.size() - 1);
        LocalDate lastProtectedEndDate = lastProtectedRound.getEndDate();
        if (lastProtectedEndDate == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return lastProtectedEndDate.plusDays(1);
    }

    private void validateFutureEndDate(
            LocalDate firstFutureStartDate,
            LocalDate requestedEndDate,
            int futureRoundCount,
            int readingPeriod,
            ScheduleCalendarService.ExcludedCalendar excludedCalendar
    ) {
        if (futureRoundCount == 0 || requestedEndDate == null) {
            return;
        }
        if (requestedEndDate.isBefore(firstFutureStartDate)) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_END_DATE_BEFORE_START_DATE);
        }

        long requiredUsableDays = (long) futureRoundCount * readingPeriod;
        long usableDaysUntilDeadline = scheduleCalendarService.countUsableDaysUntilDeadline(
                firstFutureStartDate,
                requestedEndDate,
                excludedCalendar
        );
        if (usableDaysUntilDeadline < requiredUsableDays) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_END_DATE_MISMATCH);
        }
    }

    private List<Round> createFutureRounds(
            Group group,
            int firstRoundNumber,
            LocalDate firstStartDate,
            int futureRoundCount,
            int readingPeriod,
            ScheduleCalendarService.ExcludedCalendar excludedCalendar
    ) {
        List<Round> rounds = new ArrayList<>(futureRoundCount);
        LocalDate currentStart = firstStartDate;
        for (int index = 0; index < futureRoundCount; index++) {
            LocalDate endDate = scheduleCalendarService.calculateRoundEndDate(currentStart, readingPeriod, excludedCalendar);
            rounds.add(Round.builder()
                    .roundId(UUID.randomUUID().toString())
                    .group(group)
                    .roundNumber(firstRoundNumber + index)
                    .startDate(currentStart)
                    .endDate(endDate)
                    .build());
            currentStart = endDate.plusDays(1);
        }
        return rounds;
    }

    private List<GroupScheduleRoundResponse> response(
            List<Round> protectedRounds,
            List<Round> newFutureRounds,
            int totalRoundCount
    ) {
        List<GroupScheduleRoundResponse> response = new ArrayList<>(totalRoundCount);
        protectedRounds.stream()
                .map(round -> GroupScheduleRoundResponse.of(
                        round.getRoundNumber(),
                        round.getStartDate(),
                        round.getEndDate()
                ))
                .forEach(response::add);
        newFutureRounds.stream()
                .map(round -> GroupScheduleRoundResponse.of(
                        round.getRoundNumber(),
                        round.getStartDate(),
                        round.getEndDate()
                ))
                .forEach(response::add);
        return response;
    }

    private Group findGroupByIdForUpdate(String groupId) {
        return groupRepository.findByGroupIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }

    private User findActiveUserById(String userPK) {
        User user = userRepository.findById(userPK)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessException(ErrorCode.INACTIVE_USER);
        }

        return user;
    }
}
