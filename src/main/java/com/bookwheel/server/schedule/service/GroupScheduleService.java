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
import com.bookwheel.server.schedule.dto.GroupScheduleCreateRequest;
import com.bookwheel.server.schedule.dto.GroupScheduleAssignmentResponse;
import com.bookwheel.server.schedule.dto.ExcludedDateRange;
import com.bookwheel.server.schedule.dto.GroupScheduleFutureRequest;
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
import com.bookwheel.server.wheel.dto.WheelAssignmentPlan;
import com.bookwheel.server.wheel.enums.WheelStatus;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import com.bookwheel.server.wheel.service.WheelAssignmentService;
import com.bookwheel.server.wheel.service.WheelReassignmentService;
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
    private final OwnBookRepository ownBookRepository;
    private final RoundRepository roundRepository;
    private final WheelStateRepository wheelStateRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final GroupMemberPermissionValidator memberPermissionValidator;
    private final WheelAssignmentService wheelAssignmentService;
    private final WheelReassignmentService wheelReassignmentService;
    private final FutureScheduleService futureScheduleService;
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

        LocalDate startDate = request.startDate();
        // 시작 처리는 자정 스케줄러가 담당하므로 당일 생성은 시작 시점을 놓칠 수 있다.
        if (!startDate.isAfter(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_START_DATE_NOT_FUTURE);
        }

        // 일정 확정 시점의 ACTIVE 멤버 수를 기준으로 라운드 수를 결정한다.
        // 모임 생성 직후처럼 멤버가 1명이어도 일정 설정은 저장할 수 있다.
        List<Member> activeMembers = memberRepository.findByGroup_GroupIdAndMemberStatus(groupId, MemberStatus.ACTIVE);
        int activeMemberCount = activeMembers.size();

        Integer readingPeriod = request.readingPeriod();
        if (readingPeriod == null || readingPeriod < 1) {
            throw new BusinessException(ErrorCode.GROUP_READING_PERIOD_INVALID);
        }

        LocalDate requestedEndDate = request.endDate();
        if (requestedEndDate != null && requestedEndDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_END_DATE_BEFORE_START_DATE);
        }

        // ACTIVE 멤버 수보다 1 적게 라운드 돌기. 1명일 때는 설정만 저장한다.
        int roundCount = Math.max(activeMemberCount - 1, 0);

        // 제외할 날짜(단일/범위)들을 병합하여 탐색에 최적화된 달력 객체 생성
        ScheduleCalendarService.ExcludedCalendar excludedCalendar = scheduleCalendarService.normalizeExcludedCalendar(
                request.excludedDates(),
                request.excludedDateRanges()
        );

        // 기존 일정은 새 설정으로 교체하되, 미래 책바퀴 배정은 다시 만들지 않는다.
        deleteReplaceableRecruitingSchedule(group);
        group.updateReadingPeriod(readingPeriod);
        group.updateScheduleInfo(startDate, roundCount);
        group.updateScheduleConstraints(
                requestedEndDate,
                serializeExcludedDates(request.excludedDates()),
                serializeExcludedDateRanges(request.excludedDateRanges())
        );

        if (roundCount == 0) {
            return buildScheduleResponse(group, List.of());
        }

        long requiredUsableDays = (long) roundCount * readingPeriod;

        if (requestedEndDate != null) {
            long usableDaysUntilDeadline = scheduleCalendarService.countUsableDaysUntilDeadline(
                    startDate,
                    requestedEndDate,
                    excludedCalendar
            );
            if (usableDaysUntilDeadline < requiredUsableDays) {
                throw new BusinessException(ErrorCode.GROUP_SCHEDULE_END_DATE_MISMATCH);
            }
        }

        // 핵심 로직: 라운드별 시작일과 종료일 계산 (제외된 날짜는 건너뜀)
        List<GroupScheduleRoundResponse> rounds = new ArrayList<>(roundCount);
        LocalDate currentStart = startDate;
        for (int roundNumber = 1; roundNumber <= roundCount; roundNumber++) {
            LocalDate endDate = scheduleCalendarService.calculateRoundEndDate(
                    currentStart,
                    readingPeriod,
                    excludedCalendar
            );
            rounds.add(GroupScheduleRoundResponse.of(roundNumber, currentStart, endDate));
            currentStart = endDate.plusDays(1); // 다음 라운드는 이전 라운드 종료일 다음날부터 시작
        }

        LocalDate calculatedFinalEndDate = rounds.get(rounds.size() - 1).endDate();
        if (requestedEndDate != null && calculatedFinalEndDate.isAfter(requestedEndDate)) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_END_DATE_MISMATCH);
        }

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

        List<GroupScheduleAssignmentResponse> roundResponses = rounds.stream()
                .map(round -> GroupScheduleAssignmentResponse.withoutAssignment(
                        round.roundNumber(), round.startDate(), round.endDate()
                ))
                .toList();
        return buildScheduleResponse(group, roundResponses);
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
        findActiveUserById(userPK);
        Member member = findActiveMember(groupId, userPK);

        // 저장된 배정이 없는 과거 일정도 날짜 정보는 함께 반환한다.
        List<Round> rounds = roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId);
        if (rounds.isEmpty()) {
            return buildScheduleResponse(group, List.of());
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
                            resolveSenderNickname(round, wheelState, roundByNumber, readerNicknameByRoundAndBookId)
                    );
                })
                .toList();
        return buildScheduleResponse(group, roundResponses);
    }

    private GroupScheduleResponse buildScheduleResponse(
            Group group,
            List<GroupScheduleAssignmentResponse> rounds
    ) {
        return new GroupScheduleResponse(
                group.getStartDate(),
                group.getReadingPeriod(),
                group.getScheduleEndDate(),
                deserializeExcludedDates(group.getScheduleExcludedDates()),
                deserializeExcludedDateRanges(group.getScheduleExcludedDateRanges()),
                resolveScheduleStatus(group, rounds),
                rounds
        );
    }

    private GroupScheduleStatus resolveScheduleStatus(
            Group group,
            List<GroupScheduleAssignmentResponse> rounds
    ) {
        if (group.getStartDate() == null || group.getReadingPeriod() == null) {
            return GroupScheduleStatus.NOT_CONFIGURED;
        }
        if (group.getGroupState() == State.RECRUITING
                && !group.getStartDate().isAfter(LocalDate.now(clock))) {
            return GroupScheduleStatus.RESCHEDULE_REQUIRED;
        }
        if (group.getGroupState() == State.IN_PROGRESS) {
            return GroupScheduleStatus.IN_PROGRESS;
        }
        if (group.getGroupState() == State.COMPLETE) {
            return GroupScheduleStatus.COMPLETE;
        }
        return rounds.isEmpty() ? GroupScheduleStatus.CONFIGURED : GroupScheduleStatus.READY;
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

    private WheelAssignmentPlan planInitialAssignments(
            List<Round> rounds,
            List<Member> activeMembers,
            List<OwnBook> books
    ) {
        List<WheelAssignmentPlan.Assignment> assignments = new ArrayList<>(rounds.size() * activeMembers.size());
        for (Round round : rounds) {
            List<WheelAssignmentService.WheelAssignment> roundAssignments =
                    wheelAssignmentService.assignBooks(activeMembers, books, round.getRoundNumber());
            if (roundAssignments.size() != activeMembers.size()) {
                throw new BusinessException(ErrorCode.GROUP_SCHEDULE_OWN_BOOK_REQUIRED);
            }
            roundAssignments.stream()
                    .map(assignment -> new WheelAssignmentPlan.Assignment(
                            round.getRoundId(),
                            assignment.member().getMemberId(),
                            assignment.ownBook().getOwnBookId()
                    ))
                    .forEach(assignments::add);
        }
        return new WheelAssignmentPlan(assignments);
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
        List<Member> activeMembers = memberRepository.findByGroupIdAndMemberStatusForUpdate(
                group.getGroupId(),
                MemberStatus.ACTIVE
        );
        if (activeMembers.size() < 2) {
            return false;
        }

        List<OwnBook> books = ownBookRepository.findByGroup_GroupIdIn(List.of(group.getGroupId()));
        if (!wheelAssignmentService.findMembersWithoutBook(activeMembers, books).isEmpty()) {
            return false;
        }

        Integer readingPeriod = group.getReadingPeriod();
        if (readingPeriod == null || readingPeriod < 1) {
            return false;
        }

        List<Round> rounds = roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(group.getGroupId());
        int expectedRoundCount = activeMembers.size() - 1;

        if (rounds.isEmpty() || !hasValidExistingRoundShape(rounds, expectedRoundCount)) {
            // 미리 일정을 만들지 않았거나 멤버 구성이 바뀐 경우
            // 시작 시점의 최종 ACTIVE 멤버 수와 저장된 설정으로 일정을 다시 만든다.
            deleteReplaceableRecruitingSchedule(group);
            group.invalidateSchedule();
            ScheduleCalendarService.ExcludedCalendar excludedCalendar = scheduleCalendarService.normalizeExcludedCalendar(
                    deserializeExcludedDates(group.getScheduleExcludedDates()),
                    deserializeExcludedDateRanges(group.getScheduleExcludedDateRanges())
            );
            if (!canFitSchedule(
                    startDate,
                    group.getScheduleEndDate(),
                    expectedRoundCount,
                    readingPeriod,
                    excludedCalendar
            )) {
                return false;
            }
            rounds = createRounds(group, expectedRoundCount, startDate, readingPeriod, excludedCalendar);
            group.updateScheduleInfo(startDate, expectedRoundCount);
        }

        Round firstRound = rounds.get(0);
        if (!firstRound.getStartDate().equals(startDate)) {
            return false;
        }

        // 일정 API는 날짜만 저장한다. 모임을 실제로 시작하기 직전에
        // 최종 멤버와 도서를 기준으로 전체 라운드 배정을 PLANNED로 생성한다.
        replaceInitialPlannedAssignments(rounds, activeMembers, books);
        return true;
    }

    private void replaceInitialPlannedAssignments(
            List<Round> rounds,
            List<Member> activeMembers,
            List<OwnBook> books
    ) {
        List<String> roundIds = rounds.stream().map(Round::getRoundId).toList();
        List<WheelState> existingStates = wheelStateRepository.findByRoundIdIn(roundIds);
        boolean hasStartedState = existingStates.stream()
                .anyMatch(wheelState -> wheelState.getWheelState() != WheelStatus.PLANNED);
        if (hasStartedState) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_INVALIDATION_BLOCKED_BY_WHEEL_STATE);
        }
        if (!existingStates.isEmpty()) {
            wheelStateRepository.deleteByRoundIdInAndWheelState(roundIds, WheelStatus.PLANNED);
            wheelStateRepository.flush();
        }

        WheelAssignmentPlan assignmentPlan = planInitialAssignments(rounds, activeMembers, books);
        wheelReassignmentService.savePlannedAssignments(assignmentPlan, activeMembers, books);
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

    private List<Round> createRounds(
            Group group,
            int roundCount,
            LocalDate startDate,
            int readingPeriod,
            ScheduleCalendarService.ExcludedCalendar excludedCalendar
    ) {
        List<Round> rounds = new ArrayList<>(roundCount);
        LocalDate currentStart = startDate;

        for (int roundNumber = 1; roundNumber <= roundCount; roundNumber++) {
            LocalDate endDate = scheduleCalendarService.calculateRoundEndDate(
                    currentStart,
                    readingPeriod,
                    excludedCalendar
            );
            rounds.add(Round.builder()
                    .roundId(UUID.randomUUID().toString())
                    .group(group)
                    .roundNumber(roundNumber)
                    .startDate(currentStart)
                    .endDate(endDate)
                    .build());
            currentStart = endDate.plusDays(1);
        }

        return roundRepository.saveAll(rounds);
    }

    private boolean canFitSchedule(
            LocalDate startDate,
            LocalDate requestedEndDate,
            int roundCount,
            int readingPeriod,
            ScheduleCalendarService.ExcludedCalendar excludedCalendar
    ) {
        if (requestedEndDate == null) {
            return true;
        }
        if (requestedEndDate.isBefore(startDate)) {
            return false;
        }
        long usableDays = scheduleCalendarService.countUsableDaysUntilDeadline(
                startDate,
                requestedEndDate,
                excludedCalendar
        );
        return usableDays >= (long) roundCount * readingPeriod;
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
        LocalDate localDate = LocalDate.now();

        // 1.'오늘'을 기준으로 종료일이 지난 roundId 리스트 조회
        List<String> expiredRoundIds = roundRepository.findRoundIdsByEndDateBefore(localDate);

        // 만약 끝난 라운드가 하나도 없다면(비어있다면) 메서드 종료
        if (expiredRoundIds.isEmpty()) return 0;

        // 2. expiredRoundIds에 속하면서, 아직 완료되지 않은 책바퀴 종료.
        int updated = wheelStateRepository.bulkCloseWheelStates(expiredRoundIds, WheelStatus.UNFINISHED);

        // 3. 어제(=오늘 종료된) 라운드들은 명시적으로 알림 발행 - 라운드 단위 이벤트
        List<Round> closedYesterday = roundRepository.findByEndDate(localDate.minusDays(1));
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

        // 1. 오늘 진행되어야 하는 라운드 조회
        List<Round> startingRounds = roundRepository.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(
                localDate,
                localDate
        );

        // 없을 경우, 0 리턴
        if (startingRounds.isEmpty()) return 0;

        int cnt = 0;
        List<WheelState> newWheels = new ArrayList<>();
        List<Round> startedRounds = new ArrayList<>();

        // 그룹 잠금 이후 최신 ACTIVE 멤버와 책을 조회해 멤버 변경과의 경쟁 조건을 막는다.
        for (Round round : startingRounds) {
            String groupId = round.getGroup().getGroupId();
            Group group = groupRepository.findByGroupIdForUpdate(groupId).orElse(null);
            if (group == null) continue;
            // 자정 경계에서 재생성으로 삭제된 라운드를 영속성 컨텍스트의 오래된 객체로 시작하지 않도록 DB를 재확인한다.
            boolean isCurrentRound = roundRepository
                    .existsByRoundIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                            round.getRoundId(), localDate, localDate
                    );
            if (!isCurrentRound) continue;

            List<Member> members = memberRepository
                    .findByGroup_GroupIdAndMemberStatusOrderByReadOrderAsc(groupId, MemberStatus.ACTIVE);
            List<OwnBook> books = ownBookRepository.findByGroup_GroupIdIn(List.of(groupId));

            // 멤버나 책이 없는 비정상 그룹은 스킵
            if (group.getGroupState() != State.IN_PROGRESS) continue;

            List<WheelState> existingWheelStates = wheelStateRepository.findByRoundId(round.getRoundId());
            if (!existingWheelStates.isEmpty()) {
                // 미래 책 배정은 이미 저장돼 있으므로 새로 만들지 않고, 시작일에만 실제 독서 상태로 전환한다.
                List<WheelState> plannedWheelStates = existingWheelStates.stream()
                        .filter(wheelState -> wheelState.getWheelState() == WheelStatus.PLANNED)
                        .toList();
                if (plannedWheelStates.size() == existingWheelStates.size()) {
                    plannedWheelStates.forEach(WheelState::activate);
                    cnt += plannedWheelStates.size();
                    startedRounds.add(round);
                }
                continue;
            }

            if (members.isEmpty() || books.isEmpty()) continue;
            if (!wheelAssignmentService.findMembersWithoutBook(members, books).isEmpty()) continue;

            int currentRound = round.getRoundNumber(); //현재 라운드
            // 이번 라운드의 책 배정 결과 조회
            List<WheelAssignmentService.WheelAssignment> assignments =
                    wheelAssignmentService.assignBooks(members, books, currentRound);
            if (assignments.isEmpty()) continue;

            // 배정 하나를 WheelState 엔티티로 생성
            for (WheelAssignmentService.WheelAssignment assignment : assignments) {
                WheelState newWheel = WheelState.builder()
                        .wheelStateId(UUID.randomUUID().toString())
                        .roundId(round.getRoundId())
                        .member(assignment.member())
                        .ownBook(assignment.ownBook())
                        .build();

                newWheels.add(newWheel);
                cnt++;
            }
            startedRounds.add(round);
        }
        // 한 번에 DB에 저장
        if (!newWheels.isEmpty()) {
            wheelStateRepository.saveAll(newWheels);
        }

        // 라운드 시작 알림
        for (Round round : startedRounds) {
            Group group = groupRepository.findByGroupIdForUpdate(round.getGroup().getGroupId()).orElse(null);
            if (group == null) continue;
            eventPublisher.publishEvent(new RoundStartedEvent(
                    group.getGroupId(), group.getGroupName(), round.getRoundNumber()
            ));
        }
        return cnt;
    }

    // 모든 라운드가 끝난 그룹을 COMPLETE 상태로 변경
    @Transactional
    public int closeFinishedGroups() {
        LocalDate today = LocalDate.now();

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
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
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
