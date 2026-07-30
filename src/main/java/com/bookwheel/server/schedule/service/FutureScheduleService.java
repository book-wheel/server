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
import com.bookwheel.server.schedule.dto.ExcludedDateRange;
import com.bookwheel.server.schedule.dto.GroupScheduleFutureRequest;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    public void regenerateFutureSchedule(
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

        Integer readingPeriod = request.readingPeriod();
        if (readingPeriod == null || readingPeriod < 1) {
            throw new BusinessException(ErrorCode.GROUP_READING_PERIOD_INVALID);
        }

        LocalDate today = LocalDate.now(clock);
        List<Member> activeMembers = memberRepository.findByGroup_GroupIdAndMemberStatus(groupId, MemberStatus.ACTIVE);
        List<Round> existingRounds = roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId);
        // 실제 실행 범위 안에서 오늘 시작했거나 이미 시작한 라운드만 독서 기록 보호 대상으로 삼는다.
        List<Round> protectedRounds = protectedRounds(
                existingRounds,
                today,
                group.getGroupRoundCount()
        );
        List<Round> replaceableRounds = replaceableRounds(existingRounds, protectedRounds);
        List<String> protectedRoundIds = protectedRounds.stream()
                .map(Round::getRoundId)
                .toList();
        List<WheelState> protectedStates = protectedRoundIds.isEmpty()
                ? List.of()
                : wheelStateRepository.findByRoundIdIn(protectedRoundIds);
        List<OwnBook> books = ownBookRepository.findByGroup_GroupIdIn(List.of(groupId));

        int totalRoundCount = request.totalRoundCount();
        int protectedRoundCount = protectedRounds.size();
        if (totalRoundCount < protectedRoundCount) {
            throw new BusinessException(ErrorCode.GROUP_FUTURE_SCHEDULE_TOTAL_BELOW_PROTECTED);
        }

        int maxTotalRoundCount = protectedRoundCount
                + maxFutureRoundCount(activeMembers, books, protectedStates);
        if (totalRoundCount > maxTotalRoundCount) {
            throw new BusinessException(ErrorCode.GROUP_FUTURE_SCHEDULE_TOTAL_EXCEEDS_ACTIVE_LIMIT);
        }

        int futureRoundCount = totalRoundCount - protectedRoundCount;
        ScheduleCalendarService.ExcludedCalendar excludedCalendar = scheduleCalendarService.normalizeExcludedCalendar(
                request.excludedDates(),
                request.excludedDateRanges()
        );
        LocalDate firstFutureStartDate = firstFutureStartDate(group, protectedRounds, today);
        validateFutureEndDate(
                protectedRounds,
                firstFutureStartDate,
                request.endDate(),
                futureRoundCount,
                readingPeriod,
                excludedCalendar
        );

        List<Round> newFutureRounds = createFutureRounds(
                group, protectedRoundCount + 1, firstFutureStartDate, futureRoundCount, readingPeriod, excludedCalendar
        );

        WheelAssignmentPlan futureAssignmentPlan = WheelAssignmentPlan.empty();
        if (!newFutureRounds.isEmpty()) {
            futureAssignmentPlan = wheelReassignmentService.planFutureAssignments(
                    newFutureRounds,
                    activeMembers,
                    books,
                    protectedStates
            );
        }

        List<Round> replaceableAssignmentRounds = replaceableRounds.stream()
                .filter(round -> round.getStartDate() != null && round.getStartDate().isAfter(today))
                .toList();
        List<Round> inactivePastRounds = replaceableRounds.stream()
                .filter(round -> round.getStartDate() == null || !round.getStartDate().isAfter(today))
                .toList();
        validateInactiveRoundsHaveNoAssignments(inactivePastRounds);

        List<String> replaceableRoundIds = replaceableRounds.stream()
                .map(Round::getRoundId)
                .toList();
        if (!replaceableRoundIds.isEmpty()) {
            // 새 배정 전체가 가능한지 먼저 검증한 뒤, 보호 대상이 아닌 라운드와 배정만 교체한다.
            wheelReassignmentService.deleteReplaceableFutureAssignments(replaceableAssignmentRounds);
            roundRepository.deleteByRoundIdIn(replaceableRoundIds);
            roundRepository.flush();
        }
        if (!newFutureRounds.isEmpty()) {
            roundRepository.saveAll(newFutureRounds);
            wheelReassignmentService.savePlannedAssignments(futureAssignmentPlan, activeMembers, books);
        }
        // 진행 중에는 보호된 라운드를 건드리지 않고, 이후 생성될 일정의 기본 기간만 갱신한다.
        group.updateReadingPeriod(readingPeriod);
        group.updateScheduleInfo(group.getStartDate(), totalRoundCount);
        group.updateScheduleConstraints(
                request.endDate(),
                serializeExcludedDates(request.excludedDates()),
                serializeExcludedDateRanges(request.excludedDateRanges())
        );

    }

    public FutureScheduleConstraints resolveConstraints(String groupId, int executableRoundCount) {
        // 프론트에는 이미 시작되어 반드시 보존해야 하는 라운드 기반 최소값을 제공한다.
        LocalDate today = LocalDate.now(clock);
        List<Round> rounds = roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId);
        int protectedRoundCount = protectedRounds(rounds, today, executableRoundCount).size();
        return new FutureScheduleConstraints(
                protectedRoundCount,
                Math.max(1, protectedRoundCount)
        );
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

    private List<Round> protectedRounds(
            List<Round> rounds,
            LocalDate today,
            int executableRoundCount
    ) {
        return rounds.stream()
                .filter(round -> round.getRoundNumber() != null
                        && round.getRoundNumber() <= executableRoundCount)
                .filter(round -> round.getStartDate() != null && !round.getStartDate().isAfter(today))
                .sorted(Comparator.comparing(Round::getRoundNumber))
                .toList();
    }

    private List<Round> replaceableRounds(List<Round> rounds, List<Round> protectedRounds) {
        Set<String> protectedRoundIds = protectedRounds.stream()
                .map(Round::getRoundId)
                .collect(Collectors.toSet());
        return rounds.stream()
                // 목표 인원용 비활성 날짜 틀은 날짜가 지났어도 실행 기록이 아니므로 교체할 수 있다.
                .filter(round -> !protectedRoundIds.contains(round.getRoundId()))
                .sorted(Comparator.comparing(Round::getRoundNumber))
                .toList();
    }

    private void validateInactiveRoundsHaveNoAssignments(List<Round> inactivePastRounds) {
        if (inactivePastRounds.isEmpty()) {
            return;
        }
        List<String> inactivePastRoundIds = inactivePastRounds.stream()
                .map(Round::getRoundId)
                .toList();
        // 실행 범위 밖의 지난 날짜 틀에는 배정이 없어야만 안전하게 새 미래 일정으로 교체할 수 있다.
        if (!wheelStateRepository.findByRoundIdIn(inactivePastRoundIds).isEmpty()) {
            throw new BusinessException(ErrorCode.GROUP_FUTURE_SCHEDULE_WHEEL_STATE_INVALID);
        }
    }

    private LocalDate firstFutureStartDate(Group group, List<Round> protectedRounds, LocalDate today) {
        if (protectedRounds.isEmpty()) {
            LocalDate groupStartDate = group.getStartDate();
            if (groupStartDate == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            return laterDate(groupStartDate, today.plusDays(1));
        }

        Round lastProtectedRound = protectedRounds.get(protectedRounds.size() - 1);
        LocalDate lastProtectedEndDate = lastProtectedRound.getEndDate();
        if (lastProtectedEndDate == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return laterDate(lastProtectedEndDate.plusDays(1), today.plusDays(1));
    }

    private LocalDate laterDate(LocalDate firstDate, LocalDate secondDate) {
        return firstDate.isAfter(secondDate) ? firstDate : secondDate;
    }

    private int maxFutureRoundCount(
            List<Member> activeMembers,
            List<OwnBook> books,
            List<WheelState> protectedStates
    ) {
        // 이미 읽은 책은 다시 배정할 수 없으므로 멤버마다 앞으로 읽을 수 있는 책 수를 계산한다.
        if (activeMembers.isEmpty()) {
            return 0;
        }

        Set<String> activeUserPKs = activeMembers.stream()
                .map(member -> member.getUser().getId())
                .collect(Collectors.toSet());
        List<OwnBook> eligibleBooks = books.stream()
                .filter(book -> activeUserPKs.contains(book.getOwner().getId()))
                .toList();

        Map<String, Set<String>> readBookIdsByMemberId = new HashMap<>();
        activeMembers.forEach(member -> readBookIdsByMemberId.put(member.getMemberId(), new HashSet<>()));
        protectedStates.stream()
                .filter(state -> readBookIdsByMemberId.containsKey(state.getMember().getMemberId()))
                .forEach(state -> readBookIdsByMemberId.get(state.getMember().getMemberId())
                        .add(state.getOwnBook().getOwnBookId()));

        // 가장 적게 남은 멤버도 모두 배정받을 수 있는 라운드 수를 최대값으로 사용한다.
        return activeMembers.stream()
                .mapToInt(member -> (int) eligibleBooks.stream()
                        .filter(book -> !book.getOwner().getId().equals(member.getUser().getId()))
                        .filter(book -> !readBookIdsByMemberId.get(member.getMemberId())
                                .contains(book.getOwnBookId()))
                        .count())
                .min()
                .orElse(0);
    }

    private String serializeExcludedDates(List<LocalDate> excludedDates) {
        if (excludedDates == null || excludedDates.isEmpty()) {
            return null;
        }
        return excludedDates.stream()
                .sorted()
                .map(LocalDate::toString)
                .collect(Collectors.joining(","));
    }

    private String serializeExcludedDateRanges(List<ExcludedDateRange> excludedDateRanges) {
        if (excludedDateRanges == null || excludedDateRanges.isEmpty()) {
            return null;
        }
        return excludedDateRanges.stream()
                .map(range -> range.startDate() + ":" + range.endDate())
                .collect(Collectors.joining(","));
    }

    private void validateFutureEndDate(
            List<Round> protectedRounds,
            LocalDate firstFutureStartDate,
            LocalDate requestedEndDate,
            int futureRoundCount,
            int readingPeriod,
            ScheduleCalendarService.ExcludedCalendar excludedCalendar
    ) {
        if (requestedEndDate == null) {
            return;
        }
        if (futureRoundCount == 0) {
            // 미래 라운드를 모두 제거해도 종료 제한일은 보존되는 마지막 라운드보다 빠를 수 없다.
            LocalDate lastProtectedEndDate = protectedRounds.isEmpty()
                    ? null
                    : protectedRounds.get(protectedRounds.size() - 1).getEndDate();
            if (lastProtectedEndDate != null && requestedEndDate.isBefore(lastProtectedEndDate)) {
                throw new BusinessException(ErrorCode.GROUP_SCHEDULE_END_DATE_MISMATCH);
            }
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

    private Group findGroupByIdForUpdate(String groupId) {
        return groupRepository.findByGroupIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }

    public record FutureScheduleConstraints(
            // 오늘 시작했거나 이미 시작한 라운드는 진행 기록 보호를 위해 삭제할 수 없다.
            int protectedRoundCount,
            // 전체 라운드 수는 보호 라운드 수보다 작을 수 없다.
            int minTotalRoundCount
    ) {
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
