package com.bookwheel.server.schedule.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.group.service.GroupMemberPermissionValidator;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.schedule.dto.GroupScheduleCreateRequest;
import com.bookwheel.server.schedule.dto.GroupScheduleAssignmentResponse;
import com.bookwheel.server.schedule.dto.GroupScheduleBlockingReason;
import com.bookwheel.server.schedule.dto.ExcludedDateRange;
import com.bookwheel.server.schedule.dto.GroupScheduleFutureRequest;
import com.bookwheel.server.schedule.dto.GroupSchedulePreviewResponse;
import com.bookwheel.server.schedule.dto.GroupScheduleResponse;
import com.bookwheel.server.schedule.dto.GroupScheduleRoundResponse;
import com.bookwheel.server.schedule.dto.GroupScheduleStatus;
import com.bookwheel.server.schedule.entity.Round;
import com.bookwheel.server.schedule.event.GroupCompletedEvent;
import com.bookwheel.server.schedule.event.GroupStartedEvent;
import com.bookwheel.server.schedule.event.RoundFinishedUnfinishedEvent;
import com.bookwheel.server.schedule.event.RoundStartedEvent;
import com.bookwheel.server.schedule.repository.RoundRepository;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import com.bookwheel.server.wheel.entity.WheelState;
import com.bookwheel.server.wheel.enums.WheelStatus;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupScheduleService {
    private final GroupRepository groupRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final RoundRepository roundRepository;
    private final WheelStateRepository wheelStateRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final GroupMemberPermissionValidator memberPermissionValidator;
    private final FutureScheduleService futureScheduleService;
    private final RecruitingScheduleAssignmentService recruitingScheduleAssignmentService;
    private final ScheduleCalendarService scheduleCalendarService;
    private final Clock clock;

    @Transactional
    public GroupScheduleResponse createSchedule(
            String groupId,
            GroupScheduleCreateRequest request,
            String userPK
    ) {
        Group group = findGroupByIdForUpdate(groupId);
        findActiveUserById(userPK);
        memberPermissionValidator.validateLeader(groupId, userPK);
        if (group.getGroupState() != State.RECRUITING) {
            throw new BusinessException(ErrorCode.GROUP_RECRUITING_STATE_REQUIRED);
        }
        if (isExistingScheduleStartDate(group, LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_REPLACE_NOT_ALLOWED_ON_START_DATE);
        }

        LocalDate startDate = request.startDate();
        // 시작 처리는 자정 스케줄러가 담당하므로 당일 생성은 시작 시점을 놓칠 수 있다.
        if (!startDate.isAfter(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_START_DATE_NOT_FUTURE);
        }

        Integer readingPeriod = request.readingPeriod();
        if (readingPeriod == null || readingPeriod < 1) {
            throw new BusinessException(ErrorCode.GROUP_READING_PERIOD_INVALID);
        }

        int targetMemberCount = validateTargetMemberCount(group, request.targetMemberCount());

        LocalDate requestedEndDate = request.endDate();
        if (requestedEndDate != null && requestedEndDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_END_DATE_BEFORE_START_DATE);
        }

        // 모집 현황과 무관하게 목표 인원이 서로의 책을 한 번씩 읽을 수 있는 전체 날짜 틀을 먼저 만든다.
        // 실제 실행 라운드는 시작 시점의 ACTIVE 인원에 따라 N-1개로 확정한다.
        int roundCount = targetMemberCount - 1;

        // 제외할 날짜(단일/범위)들을 병합하여 탐색에 최적화된 달력 객체 생성
        ScheduleCalendarService.ExcludedCalendar excludedCalendar = scheduleCalendarService.normalizeExcludedCalendar(
                request.excludedDates(),
                request.excludedDateRanges()
        );

        // 기존 일정은 새 설정으로 교체하되, 미래 책바퀴 배정은 다시 만들지 않는다.
        deleteReplaceableRecruitingSchedule(group);
        group.updateReadingPeriod(readingPeriod);
        group.updateSchedulePlan(startDate, roundCount, targetMemberCount);
        group.updateScheduleConstraints(
                requestedEndDate,
                serializeExcludedDates(request.excludedDates()),
                serializeExcludedDateRanges(request.excludedDateRanges())
        );

        validateScheduleEndDate(
                startDate,
                requestedEndDate,
                roundCount,
                readingPeriod,
                excludedCalendar
        );

        // 제외 날짜를 반영해 저장할 라운드별 시작일과 종료일을 계산한다.
        List<GroupScheduleRoundResponse> rounds = calculateRounds(
                roundCount,
                startDate,
                readingPeriod,
                excludedCalendar
        );

        validateCalculatedEndDate(rounds, requestedEndDate);

        // 계산된 DTO(rounds)를 Round 엔티티 리스트로 변환
        List<Round> roundEntities = rounds.stream()
                .map(round -> Round.builder()
                        .roundId(UUID.randomUUID().toString())
                        .group(group)
                        .roundNumber(round.roundNumber())
                        .startDate(round.startDate())
                        .endDate(round.endDate())
                        .build())
                .toList();

        // JPA의 saveAll()을 사용하여 한 번에 저장
        roundRepository.saveAll(roundEntities);
        recruitingScheduleAssignmentService.refreshPlannedAssignments(group);
        return getSchedule(groupId, userPK);
    }

    public GroupSchedulePreviewResponse previewSchedule(
            String groupId,
            GroupScheduleCreateRequest request,
            String userPK
    ) {
        Group group = findGroupById(groupId);
        findActiveUserById(userPK);
        memberPermissionValidator.validateLeader(groupId, userPK);
        if (group.getGroupState() != State.RECRUITING) {
            throw new BusinessException(ErrorCode.GROUP_RECRUITING_STATE_REQUIRED);
        }

        int targetMemberCount = validateTargetMemberCount(group, request.targetMemberCount());
        Integer readingPeriod = request.readingPeriod();
        if (readingPeriod == null || readingPeriod < 1) {
            throw new BusinessException(ErrorCode.GROUP_READING_PERIOD_INVALID);
        }
        LocalDate startDate = request.startDate();
        if (startDate == null || !startDate.isAfter(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_START_DATE_NOT_FUTURE);
        }

        LocalDate requestedEndDate = request.endDate();
        if (requestedEndDate != null && requestedEndDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_END_DATE_BEFORE_START_DATE);
        }

        // 미리보기 역시 현재 모집 인원이 아니라 목표 인원 기준의 전체 날짜 틀을 계산한다.
        int roundCount = targetMemberCount - 1;
        ScheduleCalendarService.ExcludedCalendar excludedCalendar = scheduleCalendarService.normalizeExcludedCalendar(
                request.excludedDates(),
                request.excludedDateRanges()
        );
        validateScheduleEndDate(
                startDate,
                requestedEndDate,
                roundCount,
                readingPeriod,
                excludedCalendar
        );
        List<GroupScheduleRoundResponse> rounds = calculateRounds(
                roundCount,
                startDate,
                readingPeriod,
                excludedCalendar
        );
        validateCalculatedEndDate(rounds, requestedEndDate);
        return new GroupSchedulePreviewResponse(
                targetMemberCount,
                roundCount,
                rounds.get(rounds.size() - 1).endDate(),
                rounds
        );
    }

    @Transactional
    public List<GroupScheduleRoundResponse> regenerateFutureSchedule(
            String groupId,
            GroupScheduleFutureRequest request,
            String userPK
    ) {
        return futureScheduleService.regenerateFutureSchedule(groupId, request, userPK);
    }

    public GroupScheduleResponse getSchedule(String groupId, String userPK) {
        Group group = findGroupById(groupId);
        // 삭제된 모임은 멤버 데이터 정리 여부와 관계없이 동일한 오류 계약을 반환한다.
        if (group.getGroupState() == State.DELETED) {
            throw new BusinessException(ErrorCode.GROUP_DELETED);
        }
        findActiveUserById(userPK);
        Member member = findActiveMember(groupId, userPK);

        // 저장된 배정이 없는 과거 일정도 날짜 정보는 함께 반환한다.
        List<Round> rounds = roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId);
        RecruitingScheduleAssignmentService.Readiness readiness =
                recruitingScheduleAssignmentService.evaluate(group);
        int executableRoundCount = resolveExecutableRoundCount(
                group,
                readiness,
                rounds.size()
        );
        if (rounds.isEmpty()) {
            return buildScheduleResponse(
                    group,
                    readiness,
                    executableRoundCount,
                    List.of()
            );
        }

        List<String> roundIds = rounds.stream().map(Round::getRoundId).toList();
        // 내 WheelState는 실제로 저장된 책과 상태를 그대로 보여 주기 위한 기준 데이터다.
        Map<String, WheelState> wheelStateByRoundId = wheelStateRepository
                .findAllByMemberIdAndRoundIdInWithBook(member.getMemberId(), roundIds)
                .stream()
                .collect(Collectors.toMap(WheelState::getRoundId, wheelState -> wheelState));
        Map<Integer, Round> roundByNumber = rounds.stream()
                .collect(Collectors.toMap(Round::getRoundNumber, round -> round));
        // 2라운드부터는 책 주인이 아니라 직전 라운드의 독자가 전달자이므로 전체 배정을 함께 조회한다.
        Map<RoundBookKey, String> readerNicknameByRoundAndBookId = wheelStateRepository
                .findAllByRoundIdInWithMemberAndBook(roundIds)
                .stream()
                .collect(Collectors.toMap(
                        wheelState -> roundBookKey(wheelState.getRoundId(), wheelState.getOwnBook().getOwnBookId()),
                        wheelState -> wheelState.getMember().getUser().getNickname()
                ));

        List<GroupScheduleAssignmentResponse> roundResponses = rounds.stream()
                .map(round -> {
                    WheelState wheelState = wheelStateByRoundId.get(round.getRoundId());
                    return GroupScheduleAssignmentResponse.of(
                            round,
                            wheelState,
                            resolveSenderNickname(round, wheelState, roundByNumber, readerNicknameByRoundAndBookId),
                            round.getRoundNumber() <= executableRoundCount
                    );
                })
                .toList();
        return buildScheduleResponse(
                group,
                readiness,
                executableRoundCount,
                roundResponses
        );
    }

    private GroupScheduleResponse buildScheduleResponse(
            Group group,
            RecruitingScheduleAssignmentService.Readiness readiness,
            int executableRoundCount,
            List<GroupScheduleAssignmentResponse> rounds
    ) {
        Integer resolvedTargetMemberCount = resolveTargetMemberCount(group, rounds.size());
        LocalDate plannedEndDate = rounds.isEmpty()
                ? null
                : rounds.get(rounds.size() - 1).endDate();
        LocalDate executableEndDate = executableRoundCount == 0
                ? null
                : rounds.get(executableRoundCount - 1).endDate();
        GroupScheduleStatus scheduleStatus = resolveScheduleStatus(
                group,
                readiness,
                resolvedTargetMemberCount
        );
        List<GroupScheduleBlockingReason> blockingReasons = new ArrayList<>(readiness.blockingReasons());
        if (scheduleStatus == GroupScheduleStatus.RESCHEDULE_REQUIRED) {
            blockingReasons.add(GroupScheduleBlockingReason.START_DATE_PASSED);
        }
        return new GroupScheduleResponse(
                group.getStartDate(),
                group.getReadingPeriod(),
                group.getScheduleEndDate(),
                deserializeExcludedDates(group.getScheduleExcludedDates()),
                deserializeExcludedDateRanges(group.getScheduleExcludedDateRanges()),
                scheduleStatus,
                resolvedTargetMemberCount,
                readiness.currentMemberCount(),
                scheduleStatus == GroupScheduleStatus.READY,
                List.copyOf(blockingReasons),
                readiness.missingBookMembers(),
                rounds.size(),
                executableRoundCount,
                plannedEndDate,
                executableEndDate,
                rounds
        );
    }

    private void validateScheduleEndDate(
            LocalDate startDate,
            LocalDate requestedEndDate,
            int roundCount,
            int readingPeriod,
            ScheduleCalendarService.ExcludedCalendar excludedCalendar
    ) {
        if (requestedEndDate == null) {
            return;
        }
        long requiredUsableDays = (long) roundCount * readingPeriod;
        long usableDaysUntilDeadline = scheduleCalendarService.countUsableDaysUntilDeadline(
                startDate,
                requestedEndDate,
                excludedCalendar
        );
        if (usableDaysUntilDeadline < requiredUsableDays) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_END_DATE_MISMATCH);
        }
    }

    private List<GroupScheduleRoundResponse> calculateRounds(
            int roundCount,
            LocalDate startDate,
            int readingPeriod,
            ScheduleCalendarService.ExcludedCalendar excludedCalendar
    ) {
        List<GroupScheduleRoundResponse> rounds = new ArrayList<>(roundCount);
        LocalDate currentStart = startDate;
        for (int roundNumber = 1; roundNumber <= roundCount; roundNumber++) {
            LocalDate endDate = scheduleCalendarService.calculateRoundEndDate(
                    currentStart,
                    readingPeriod,
                    excludedCalendar
            );
            rounds.add(GroupScheduleRoundResponse.of(roundNumber, currentStart, endDate));
            currentStart = endDate.plusDays(1);
        }
        return rounds;
    }

    private void validateCalculatedEndDate(
            List<GroupScheduleRoundResponse> rounds,
            LocalDate requestedEndDate
    ) {
        if (requestedEndDate == null || rounds.isEmpty()) {
            return;
        }
        LocalDate calculatedFinalEndDate = rounds.get(rounds.size() - 1).endDate();
        if (calculatedFinalEndDate.isAfter(requestedEndDate)) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_END_DATE_MISMATCH);
        }
    }

    // 모집 중에는 READY로 완성된 현재 인원 기준 N-1개를, 시작 후에는 확정된 groupRoundCount를 사용한다.
    private int resolveExecutableRoundCount(
            Group group,
            RecruitingScheduleAssignmentService.Readiness readiness,
            int storedRoundCount
    ) {
        if (storedRoundCount == 0) {
            return 0;
        }
        if (group.getGroupState() == State.RECRUITING) {
            // 시작일을 놓친 일정은 READY 선행 조건이 남아 있어도 더는 실행 대상이 아니다.
            if (group.getStartDate() != null && group.getStartDate().isBefore(LocalDate.now(clock))) {
                return 0;
            }
            return readiness.ready()
                    ? Math.min(readiness.currentMemberCount() - 1, storedRoundCount)
                    : 0;
        }
        return Math.min(Math.max(group.getGroupRoundCount(), 0), storedRoundCount);
    }

    private GroupScheduleStatus resolveScheduleStatus(
            Group group,
            RecruitingScheduleAssignmentService.Readiness readiness,
            Integer resolvedTargetMemberCount
    ) {
        if (group.getGroupState() == State.IN_PROGRESS) {
            return GroupScheduleStatus.IN_PROGRESS;
        }
        if (group.getGroupState() == State.COMPLETE) {
            return GroupScheduleStatus.COMPLETE;
        }
        if (resolvedTargetMemberCount == null
                || group.getStartDate() == null
                || group.getReadingPeriod() == null) {
            return GroupScheduleStatus.NOT_CONFIGURED;
        }
        if (group.getGroupState() == State.RECRUITING
                && group.getStartDate().isBefore(LocalDate.now(clock))) {
            return GroupScheduleStatus.RESCHEDULE_REQUIRED;
        }
        // 최소 2명과 각자의 책, 현재 인원 기준 N-1개 라운드 배정이 준비되면 목표 인원 전에도 READY다.
        return readiness.ready() ? GroupScheduleStatus.READY : GroupScheduleStatus.CONFIGURED;
    }

    private int validateTargetMemberCount(Group group, Integer targetMemberCount) {
        long currentMemberCount = memberRepository.countByGroup_GroupIdAndMemberStatus(
                group.getGroupId(),
                MemberStatus.ACTIVE
        );
        // 목표 인원은 일정 틀의 상한이므로 현재 인원보다 작거나 모임 최대 인원보다 클 수 없다.
        if (targetMemberCount == null
                || targetMemberCount < 2
                || targetMemberCount > Group.MAX_MEMBER_COUNT
                || group.getMaxMembers() == null
                || targetMemberCount > group.getMaxMembers()
                || targetMemberCount < currentMemberCount) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_TARGET_MEMBER_INVALID);
        }
        return targetMemberCount;
    }

    private boolean isExistingScheduleStartDate(Group group, LocalDate date) {
        // 시작 당일에는 준비된 라운드와 배정을 임의로 교체하지 못하게 한다.
        return group.getStartDate() != null
                && roundRepository.existsByGroup_GroupId(group.getGroupId())
                && group.getStartDate().equals(date);
    }

    // target_member_count 도입 전에 생성된 일정은 저장된 전체 라운드 수로 목표 인원을 추론한다.
    private Integer resolveTargetMemberCount(Group group, int storedRoundCount) {
        if (group.getTargetMemberCount() != null) {
            return group.getTargetMemberCount();
        }
        return storedRoundCount == 0 ? null : storedRoundCount + 1;
    }

    private String resolveSenderNickname(
            Round round,
            WheelState wheelState,
            Map<Integer, Round> roundByNumber,
            Map<RoundBookKey, String> readerNicknameByRoundAndBookId
    ) {
        if (wheelState == null) {
            return null;
        }

        // 첫 라운드는 원래 책 주인이, 이후 라운드는 직전 라운드의 해당 책 독자가 전달자다.
        Round previousRound = roundByNumber.get(round.getRoundNumber() - 1);
        if (previousRound == null) {
            return wheelState.getOwnBook().getOwner().getNickname();
        }

        return readerNicknameByRoundAndBookId.getOrDefault(
                roundBookKey(previousRound.getRoundId(), wheelState.getOwnBook().getOwnBookId()),
                wheelState.getOwnBook().getOwner().getNickname()
        );
    }

    private RoundBookKey roundBookKey(String roundId, String ownBookId) {
        return new RoundBookKey(roundId, ownBookId);
    }

    private void deleteReplaceableRecruitingSchedule(Group group) {
        List<Round> rounds = roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(group.getGroupId());
        if (rounds.isEmpty()) {
            return;
        }

        List<String> roundIds = rounds.stream()
                .map(Round::getRoundId)
                .toList();
        List<WheelState> wheelStates = wheelStateRepository.findByRoundIdIn(roundIds);
        boolean hasStartedWheelState = wheelStates.stream()
                .anyMatch(wheelState -> wheelState.getWheelState() != WheelStatus.PLANNED);
        if (hasStartedWheelState) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_INVALIDATION_BLOCKED_BY_WHEEL_STATE);
        }

        wheelStateRepository.deleteByRoundIdInAndWheelState(roundIds, WheelStatus.PLANNED);
        roundRepository.deleteByGroup_GroupId(group.getGroupId());
    }

    // 오늘이 예정 시작일인 그룹만 진행 중으로 변경한다.
    // 당일에 조건을 충족하지 못한 그룹은 리더가 새 시작일을 설정할 때까지 RECRUITING을 유지한다.
    @Transactional
    public int updateStartedGroupsToInProgress() {
        LocalDate localDate = LocalDate.now(clock);

        // 알림 대상 그룹을 먼저 조회 (벌크 업데이트 후에는 식별이 어려움)
        List<Group> startingGroups = groupRepository.findByGroupStateAndStartDate(
                State.RECRUITING, localDate
        );

        List<Group> startableGroups = new ArrayList<>();
        for (Group candidateGroup : startingGroups) {
            // 시작 처리와 멤버 변경이 동시에 일어나도 같은 모임을 중복 판단하지 않도록 잠근다.
            Optional<Group> lockedGroup = groupRepository.findByGroupIdForUpdate(candidateGroup.getGroupId());
            if (lockedGroup.isEmpty()) {
                continue;
            }

            Group group = lockedGroup.get();
            boolean startsToday = localDate.equals(group.getStartDate());
            if (group.getGroupState() == State.RECRUITING
                    && startsToday
                    && prepareStartableSchedule(group, localDate)) {
                startableGroups.add(group);
            }
        }

        if (startableGroups.isEmpty()) {
            return 0;
        }

        List<String> startableGroupIds = startableGroups.stream()
                .map(Group::getGroupId)
                .toList();

        int updated = groupRepository.updateGroupStateToInProcessByGroupIds(
                State.IN_PROGRESS,
                State.RECRUITING,
                startableGroupIds
        );

        for (Group group : startableGroups) {
            eventPublisher.publishEvent(new GroupStartedEvent(group.getGroupId(), group.getGroupName()));
        }
        return updated;
    }

    private boolean prepareStartableSchedule(Group group, LocalDate startDate) {
        // 시작 판단 중 멤버 승인·탈퇴가 끼어들지 않도록 ACTIVE 멤버를 잠근 상태로 확인한다.
        List<Member> activeMembers = memberRepository.findByGroupIdAndMemberStatusForUpdate(
                group.getGroupId(),
                MemberStatus.ACTIVE
        );
        // 기존 일정은 라운드 수 + 1로 목표 인원을 복원해 배포 순서와 무관하게 시작할 수 있게 한다.
        List<Round> rounds = roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(group.getGroupId());
        Integer targetMemberCount = resolveTargetMemberCount(group, rounds.size());
        if (targetMemberCount == null
                || activeMembers.size() < 2
                || activeMembers.size() > targetMemberCount) {
            return false;
        }

        // 목표 인원 기준으로 만든 전체 날짜 틀은 시작 당일에도 삭제하거나 다시 계산하지 않는다.
        if (!hasValidExistingRoundShape(rounds, targetMemberCount - 1)) {
            return false;
        }

        Round firstRound = rounds.get(0);
        if (!firstRound.getStartDate().equals(startDate)) {
            return false;
        }

        // 시작일에는 라운드나 배정을 만들지 않고 모집 중에 준비된 계획을 그대로 사용한다.
        if (!recruitingScheduleAssignmentService.isReady(group)) {
            return false;
        }

        // 완료 판단은 목표 인원이 아니라 시작 시점의 실제 인원으로 실행하는 N-1개 라운드를 기준으로 한다.
        group.initializeTargetMemberCount(targetMemberCount);
        group.confirmExecutableRoundCount(activeMembers.size() - 1);
        return true;
    }

    private boolean hasValidExistingRoundShape(List<Round> rounds, int expectedRoundCount) {
        if (rounds.size() != expectedRoundCount) {
            return false;
        }

        LocalDate previousEndDate = null;
        for (int index = 0; index < rounds.size(); index++) {
            Round round = rounds.get(index);
            int expectedRoundNumber = index + 1;
            if (!Objects.equals(round.getRoundNumber(), expectedRoundNumber)) {
                return false;
            }
            if (round.getStartDate() == null || round.getEndDate() == null) {
                return false;
            }
            if (round.getStartDate().isAfter(round.getEndDate())) {
                return false;
            }
            // 라운드 번호와 날짜가 연속되지 않으면 기존 일정은 손상된 것으로 본다.
            if (previousEndDate != null && !round.getStartDate().equals(previousEndDate.plusDays(1))) {
                return false;
            }
            previousEndDate = round.getEndDate();
        }

        return true;
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

    private List<LocalDate> deserializeExcludedDates(String serializedDates) {
        if (serializedDates == null || serializedDates.isBlank()) {
            return List.of();
        }
        return Arrays.stream(serializedDates.split(","))
                .map(LocalDate::parse)
                .toList();
    }

    private String serializeExcludedDateRanges(List<ExcludedDateRange> excludedDateRanges) {
        if (excludedDateRanges == null || excludedDateRanges.isEmpty()) {
            return null;
        }
        return excludedDateRanges.stream()
                .map(range -> range.startDate() + ":" + range.endDate())
                .collect(Collectors.joining(","));
    }

    private List<ExcludedDateRange> deserializeExcludedDateRanges(String serializedRanges) {
        if (serializedRanges == null || serializedRanges.isBlank()) {
            return List.of();
        }
        return Arrays.stream(serializedRanges.split(","))
                .map(serializedRange -> serializedRange.split(":"))
                .map(parts -> new ExcludedDateRange(LocalDate.parse(parts[0]), LocalDate.parse(parts[1])))
                .toList();
    }

    // 끝난 라운드를 종료시키는 로직
    @Transactional
    public int closeExpiredWheelStates() {
        LocalDate localDate = LocalDate.now(clock);

        // 1.'오늘'을 기준으로 종료일이 지난 roundId 리스트 조회
        List<String> expiredRoundIds = roundRepository.findExecutableRoundIdsByEndDateBefore(
                localDate,
                State.IN_PROGRESS
        );

        // 만약 끝난 라운드가 하나도 없다면(비어있다면) 메서드 종료
        if (expiredRoundIds.isEmpty()) return 0;

        // 2. expiredRoundIds에 속하면서, 아직 완료되지 않은 책바퀴 종료.
        int updated = wheelStateRepository.bulkCloseWheelStates(expiredRoundIds, WheelStatus.UNFINISHED);

        // 3. 어제(=오늘 종료된) 라운드들은 명시적으로 알림 발행 - 라운드 단위 이벤트
        List<Round> closedYesterday = roundRepository.findExecutableRoundsByEndDate(
                localDate.minusDays(1),
                State.IN_PROGRESS
        );
        for (Round round : closedYesterday) {
            Group group = round.getGroup();
            eventPublisher.publishEvent(new RoundFinishedUnfinishedEvent(
                    group.getGroupId(), group.getGroupName(), round.getRoundNumber()
            ));
        }
        return updated;
    }

    @Transactional
    public int startRoundWheelState() {
        LocalDate localDate = LocalDate.now(clock);
        List<Round> startingRounds = roundRepository.findExecutableRoundsContainingDate(
                localDate,
                State.IN_PROGRESS
        );
        if (startingRounds.isEmpty()) {
            return 0;
        }

        int cnt = 0;
        List<Round> startedRounds = new ArrayList<>();

        for (Round round : startingRounds) {
            String groupId = round.getGroup().getGroupId();
            Group group = groupRepository.findByGroupIdForUpdate(groupId).orElse(null);
            if (group == null || group.getGroupState() != State.IN_PROGRESS) {
                continue;
            }

            boolean isCurrentRound = roundRepository
                    .existsExecutableRoundContainingDate(
                            round.getRoundId(),
                            localDate,
                            State.IN_PROGRESS
                    );
            if (!isCurrentRound) {
                continue;
            }

            List<WheelState> existingWheelStates = wheelStateRepository.findByRoundId(round.getRoundId());
            if (existingWheelStates.isEmpty()) {
                continue;
            }

            List<WheelState> plannedWheelStates = existingWheelStates.stream()
                    .filter(wheelState -> wheelState.getWheelState() == WheelStatus.PLANNED)
                    .toList();
            if (plannedWheelStates.size() != existingWheelStates.size()) {
                continue;
            }

            plannedWheelStates.forEach(WheelState::activate);
            cnt += plannedWheelStates.size();
            startedRounds.add(round);
        }

        for (Round round : startedRounds) {
            Group group = groupRepository.findByGroupIdForUpdate(round.getGroup().getGroupId()).orElse(null);
            if (group == null) {
                continue;
            }
            eventPublisher.publishEvent(new RoundStartedEvent(
                    group.getGroupId(), group.getGroupName(), round.getRoundNumber()
            ));
        }
        return cnt;
    }

    // 모든 라운드가 끝난 그룹을 COMPLETE 상태로 변경
    @Transactional
    public int closeFinishedGroups() {
        LocalDate today = LocalDate.now(clock);

        List<Group> completing = groupRepository.findGroupsBecomingComplete(State.IN_PROGRESS, today);

        int updated = groupRepository.updateFinishedGroupsToComplete(
                State.COMPLETE,
                State.IN_PROGRESS,
                today
        );

        for (Group group : completing) {
            eventPublisher.publishEvent(new GroupCompletedEvent(group.getGroupId(), group.getGroupName()));
        }
        return updated;
    }

    private Group findGroupByIdForUpdate(String groupId) {
        return groupRepository.findByGroupIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }

    private Group findGroupById(String groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }

    private Member findActiveMember(String groupId, String userPK) {
        Member member = memberRepository.findByGroup_GroupIdAndUser_Id(groupId, userPK)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_ACTIVE_MEMBER_ONLY));
        // 미래 일정은 현재 모임에 참여 중인 멤버만 조회할 수 있다.
        if (member.getMemberStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.GROUP_ACTIVE_MEMBER_ONLY);
        }
        return member;
    }

    private record RoundBookKey(String roundId, String ownBookId) {
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
