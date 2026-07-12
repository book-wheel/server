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
import com.bookwheel.server.schedule.dto.GroupScheduleFutureRequest;
import com.bookwheel.server.schedule.dto.GroupScheduleRoundResponse;
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
import java.time.temporal.ChronoUnit;
import java.util.*;

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
    public List<GroupScheduleRoundResponse> createSchedule(
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

        // ACTIVE 멤버 수를 기준으로 총 라운드 수를 결정
        List<Member> activeMembers = memberRepository.findByGroup_GroupIdAndMemberStatus(groupId, MemberStatus.ACTIVE);
        int activeMemberCount = activeMembers.size();
        if (activeMemberCount < 2) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_ACTIVE_MEMBER_REQUIRED);
        }

        List<OwnBook> books = ownBookRepository.findByGroup_GroupIdIn(List.of(groupId));
        if (!wheelAssignmentService.findMembersWithoutBook(activeMembers, books).isEmpty()) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_OWN_BOOK_REQUIRED);
        }

        Integer readingPeriod = group.getReadingPeriod();
        if (readingPeriod == null || readingPeriod < 1) {
            throw new BusinessException(ErrorCode.GROUP_READING_PERIOD_INVALID);
        }

        LocalDate startDate = request.startDate();
        LocalDate requestedEndDate = request.endDate();
        if (requestedEndDate != null && requestedEndDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_END_DATE_BEFORE_START_DATE);
        }

        // ACTIVE 멤버 수보다 1 적게 라운드 돌기
        int roundCount = activeMemberCount - 1;

        // 제외할 날짜(단일/범위)들을 병합하여 탐색에 최적화된 달력 객체 생성
        ScheduleCalendarService.ExcludedCalendar excludedCalendar = scheduleCalendarService.normalizeExcludedCalendar(
                request.excludedDates(),
                request.excludedDateRanges()
        );
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

        // 기존 스케줄 초기화
        deleteReplaceableRecruitingSchedule(group);
        group.updateScheduleInfo(startDate, roundCount);

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
        WheelAssignmentPlan assignmentPlan = planInitialAssignments(roundEntities, activeMembers, books);
        wheelReassignmentService.savePlannedAssignments(assignmentPlan, activeMembers, books);

        return rounds;
    }

    @Transactional
    public List<GroupScheduleRoundResponse> regenerateFutureSchedule(
            String groupId,
            GroupScheduleFutureRequest request,
            String userPK
    ) {
        return futureScheduleService.regenerateFutureSchedule(groupId, request, userPK);
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

    // 오늘부터 독서를 시작해야하는 그룹을 찾아서 진행중으로 변경
    @Transactional
    public int updateStartedGroupsToInProgress() {
        LocalDate localDate = LocalDate.now();

        // 알림 대상 그룹을 먼저 조회 (벌크 업데이트 후에는 식별이 어려움)
        List<Group> startingGroups = groupRepository.findByGroupStateAndStartDateLessThanEqual(
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
            if (group.getGroupState() == State.RECRUITING && prepareStartableSchedule(group, localDate)) {
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

        if (rounds.isEmpty()) {
            List<Round> defaultRounds = createDefaultRounds(group, expectedRoundCount, startDate, readingPeriod);
            // 자동 시작으로 생성한 일정도 수동 생성과 동일하게 모든 미래 배정을 PLANNED로 저장한다.
            WheelAssignmentPlan assignmentPlan = planInitialAssignments(defaultRounds, activeMembers, books);
            wheelReassignmentService.savePlannedAssignments(assignmentPlan, activeMembers, books);
            group.updateScheduleInfo(startDate, expectedRoundCount);
            return true;
        }

        // 저장된 계획이 현재 ACTIVE 멤버 수와 맞지 않으면 자동 시작하지 않는다.
        if (!hasValidExistingRoundShape(rounds, expectedRoundCount)) {
            return false;
        }

        Round firstRound = rounds.get(0);
        if (firstRound.getStartDate().isAfter(startDate)) {
            return false;
        }
        if (firstRound.getStartDate().isBefore(startDate)) {
            shiftRoundsFromStartDate(group, rounds, startDate, readingPeriod);
        }

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

    private List<Round> createDefaultRounds(Group group, int roundCount, LocalDate startDate, int readingPeriod) {
        ScheduleCalendarService.ExcludedCalendar excludedCalendar = scheduleCalendarService.emptyCalendar();
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

    private void shiftRoundsFromStartDate(
            Group group,
            List<Round> rounds,
            LocalDate startDate,
            int readingPeriod
    ) {
        LocalDate currentStart = startDate;

        for (Round round : rounds) {
            long roundDays = readingPeriod - 1L;
            if (round.getEndDate() != null) {
                roundDays = ChronoUnit.DAYS.between(round.getStartDate(), round.getEndDate());
            }

            LocalDate endDate = currentStart.plusDays(Math.max(roundDays, 0L));
            round.updateSchedule(currentStart, endDate);
            currentStart = endDate.plusDays(1);
        }

        group.updateScheduleInfo(startDate, group.getGroupRoundCount());
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

    private User findActiveUserById(String userPK) {
        User user = userRepository.findById(userPK)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessException(ErrorCode.INACTIVE_USER);
        }

        return user;
    }
}
