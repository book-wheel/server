package com.bookwheel.server.schedule.service;

import com.bookwheel.server.book.entity.OwnBook;
import com.bookwheel.server.book.repository.OwnBookRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.schedule.dto.GroupScheduleBlockingReason;
import com.bookwheel.server.schedule.dto.GroupScheduleMissingBookMemberResponse;
import com.bookwheel.server.schedule.entity.Round;
import com.bookwheel.server.schedule.repository.RoundRepository;
import com.bookwheel.server.wheel.dto.WheelAssignmentPlan;
import com.bookwheel.server.wheel.entity.WheelState;
import com.bookwheel.server.wheel.enums.WheelStatus;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import com.bookwheel.server.wheel.service.WheelAssignmentService;
import com.bookwheel.server.wheel.service.WheelReassignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
// 모집 중에는 목표 인원 기준 날짜 틀을 보존하면서 현재 ACTIVE 멤버로 실행 가능한 배정만 관리한다.
public class RecruitingScheduleAssignmentService {
    private final MemberRepository memberRepository;
    private final OwnBookRepository ownBookRepository;
    private final RoundRepository roundRepository;
    private final WheelStateRepository wheelStateRepository;
    private final WheelAssignmentService wheelAssignmentService;
    private final WheelReassignmentService wheelReassignmentService;

    @Transactional
    public Readiness refreshPlannedAssignments(Group group) {
        // 모집 중이 아니면 기존 진행 기록을 건드리지 않고 조회용 상태만 계산한다.
        if (group.getGroupState() != State.RECRUITING) {
            return evaluate(group);
        }

        // 멤버·책·읽기 순서가 바뀔 때 날짜 틀은 유지하고 교체 가능한 PLANNED 배정만 지운다.
        List<Round> rounds = rounds(group.getGroupId());
        clearPlannedAssignments(rounds);

        List<Member> activeMembers = activeMembers(group.getGroupId());
        List<OwnBook> books = books(group.getGroupId());
        if (!canCreateAssignments(group, rounds, activeMembers, books)) {
            return evaluate(group, rounds, activeMembers, books, List.of());
        }

        // 현재 인원이 서로의 책을 한 번씩 읽는 데 필요한 앞쪽 N-1개 라운드만 배정한다.
        List<Round> executableRounds = executableRounds(rounds, activeMembers.size());
        WheelAssignmentPlan plan = planAssignments(executableRounds, activeMembers, books);
        wheelReassignmentService.savePlannedAssignments(plan, activeMembers, books);
        return evaluate(group);
    }

    @Transactional
    public void clearPlannedAssignments(Group group) {
        if (group.getGroupState() != State.RECRUITING) {
            return;
        }
        clearPlannedAssignments(rounds(group.getGroupId()));
    }

    public Readiness evaluate(Group group) {
        List<Round> rounds = rounds(group.getGroupId());
        List<Member> activeMembers = activeMembers(group.getGroupId());
        List<OwnBook> books = books(group.getGroupId());
        List<WheelState> states = states(rounds);
        return evaluate(group, rounds, activeMembers, books, states);
    }

    public boolean isReady(Group group) {
        return evaluate(group).ready();
    }

    private Readiness evaluate(
            Group group,
            List<Round> rounds,
            List<Member> activeMembers,
            List<OwnBook> books,
            List<WheelState> states
    ) {
        List<GroupScheduleBlockingReason> reasons = new ArrayList<>();
        Integer targetMemberCount = resolveTargetMemberCount(group, rounds);
        int activeMemberCount = activeMembers.size();
        List<GroupScheduleMissingBookMemberResponse> missingBookMembers =
                wheelAssignmentService.findMembersWithoutBook(activeMembers, books)
                .stream()
                .map(GroupScheduleMissingBookMemberResponse::from)
                .toList();

        if (group.getGroupState() != State.RECRUITING) {
            return new Readiness(false, activeMemberCount, List.of(), missingBookMembers);
        }

        if (targetMemberCount == null || targetMemberCount < 2) {
            reasons.add(GroupScheduleBlockingReason.ROUND_PLAN_INCOMPLETE);
            return new Readiness(false, activeMemberCount, List.copyOf(reasons), missingBookMembers);
        }

        // 목표 인원을 다 채우지 않아도 시작할 수 있지만 책을 교환하려면 최소 2명은 필요하다.
        if (activeMemberCount < 2) {
            reasons.add(GroupScheduleBlockingReason.MINIMUM_ACTIVE_MEMBER_COUNT_NOT_REACHED);
        } else if (activeMemberCount > targetMemberCount) {
            reasons.add(GroupScheduleBlockingReason.TARGET_MEMBER_COUNT_EXCEEDED);
        }

        if (!missingBookMembers.isEmpty()) {
            reasons.add(GroupScheduleBlockingReason.MEMBER_BOOK_NOT_REGISTERED);
        }

        boolean roundPlanComplete = hasExpectedRoundShape(rounds, targetMemberCount - 1);
        if (!roundPlanComplete) {
            reasons.add(GroupScheduleBlockingReason.ROUND_PLAN_INCOMPLETE);
        }

        // 인원·책·라운드 틀이 정상인데도 저장된 배정만 불완전할 때 별도 오류로 구분한다.
        boolean assignmentPrerequisitesComplete = activeMemberCount >= 2
                && activeMemberCount <= targetMemberCount
                && missingBookMembers.isEmpty()
                && roundPlanComplete;
        if (assignmentPrerequisitesComplete) {
            List<Round> executableRounds = executableRounds(rounds, activeMemberCount);
            boolean assignmentsComplete = hasCompletePlannedAssignments(
                    executableRounds,
                    activeMembers,
                    states
            );
            if (!assignmentsComplete) {
                reasons.add(GroupScheduleBlockingReason.ASSIGNMENT_INCOMPLETE);
            }
        }

        return new Readiness(
                reasons.isEmpty(),
                activeMemberCount,
                List.copyOf(reasons),
                missingBookMembers
        );
    }

    private boolean canCreateAssignments(
            Group group,
            List<Round> rounds,
            List<Member> activeMembers,
            List<OwnBook> books
    ) {
        Integer targetMemberCount = resolveTargetMemberCount(group, rounds);
        return targetMemberCount != null
                && activeMembers.size() >= 2
                && activeMembers.size() <= targetMemberCount
                && hasExpectedRoundShape(rounds, targetMemberCount - 1)
                && wheelAssignmentService.findMembersWithoutBook(activeMembers, books).isEmpty();
    }

    // 기존 일정 데이터에 목표 인원이 없으면 저장된 전체 라운드 수 + 1로 동일한 의미를 복원한다.
    private Integer resolveTargetMemberCount(Group group, List<Round> rounds) {
        if (group.getTargetMemberCount() != null) {
            return group.getTargetMemberCount();
        }
        return rounds.isEmpty() ? null : rounds.size() + 1;
    }

    // 전체 날짜 틀 중 현재 인원으로 실제 실행할 수 있는 앞쪽 라운드만 선택한다.
    private List<Round> executableRounds(List<Round> rounds, int activeMemberCount) {
        int executableRoundCount = Math.max(activeMemberCount - 1, 0);
        if (rounds.size() < executableRoundCount) {
            return List.of();
        }
        return rounds.subList(0, executableRoundCount);
    }

    // 실행 대상 라운드마다 현재 ACTIVE 멤버 전원의 PLANNED 배정이 정확히 하나씩 있는지 검증한다.
    private boolean hasCompletePlannedAssignments(
            List<Round> rounds,
            List<Member> activeMembers,
            List<WheelState> states
    ) {
        int expectedAssignmentCount = rounds.size() * activeMembers.size();
        if (expectedAssignmentCount == 0
                || states.size() != expectedAssignmentCount
                || states.stream().anyMatch(state -> state.getWheelState() != WheelStatus.PLANNED)) {
            return false;
        }

        Set<AssignmentKey> expectedAssignments = new HashSet<>(expectedAssignmentCount);
        for (Round round : rounds) {
            for (Member member : activeMembers) {
                expectedAssignments.add(new AssignmentKey(round.getRoundId(), member.getMemberId()));
            }
        }

        Set<AssignmentKey> actualAssignments = new HashSet<>(expectedAssignmentCount);
        for (WheelState state : states) {
            actualAssignments.add(new AssignmentKey(state.getRoundId(), state.getMember().getMemberId()));
        }
        return actualAssignments.equals(expectedAssignments);
    }

    private WheelAssignmentPlan planAssignments(
            List<Round> rounds,
            List<Member> activeMembers,
            List<OwnBook> books
    ) {
        // 각 실행 라운드의 회전 번호를 그대로 사용해 모든 멤버에게 서로 다른 책을 배정한다.
        List<WheelAssignmentPlan.Assignment> assignments =
                new ArrayList<>(rounds.size() * activeMembers.size());

        for (Round round : rounds) {
            List<WheelAssignmentService.WheelAssignment> roundAssignments =
                    wheelAssignmentService.assignBooks(activeMembers, books, round.getRoundNumber());
            if (roundAssignments.size() != activeMembers.size()) {
                throw new BusinessException(ErrorCode.WHEEL_REASSIGNMENT_IMPOSSIBLE);
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

    private void clearPlannedAssignments(List<Round> rounds) {
        if (rounds.isEmpty()) {
            return;
        }

        List<String> roundIds = rounds.stream().map(Round::getRoundId).toList();
        List<WheelState> states = wheelStateRepository.findByRoundIdInForUpdate(roundIds);
        // READY/IN_PROGRESS 등 실제 진행 상태가 섞여 있으면 모집 배정으로 간주해 지우지 않는다.
        boolean hasStartedState = states.stream()
                .anyMatch(state -> state.getWheelState() != WheelStatus.PLANNED);
        if (hasStartedState) {
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_INVALIDATION_BLOCKED_BY_WHEEL_STATE);
        }
        if (!states.isEmpty()) {
            wheelStateRepository.deleteByRoundIdInAndWheelState(roundIds, WheelStatus.PLANNED);
            wheelStateRepository.flush();
        }
    }

    private boolean hasExpectedRoundShape(List<Round> rounds, int expectedRoundCount) {
        if (rounds.size() != expectedRoundCount) {
            return false;
        }

        for (int index = 0; index < rounds.size(); index++) {
            Round round = rounds.get(index);
            if (!Objects.equals(round.getRoundNumber(), index + 1)
                    || round.getStartDate() == null
                    || round.getEndDate() == null
                    || round.getStartDate().isAfter(round.getEndDate())) {
                return false;
            }
        }
        return true;
    }

    private List<Round> rounds(String groupId) {
        return roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId);
    }

    private List<Member> activeMembers(String groupId) {
        return memberRepository.findByGroup_GroupIdAndMemberStatusOrderByReadOrderAsc(
                groupId,
                MemberStatus.ACTIVE
        );
    }

    private List<OwnBook> books(String groupId) {
        return ownBookRepository.findByGroup_GroupIdIn(List.of(groupId));
    }

    private List<WheelState> states(List<Round> rounds) {
        if (rounds.isEmpty()) {
            return List.of();
        }
        return wheelStateRepository.findByRoundIdIn(rounds.stream().map(Round::getRoundId).toList());
    }

    public record Readiness(
            // 현재 모집 인원과 도서 기준으로 실행 대상 라운드를 시작할 수 있는지 나타낸다.
            boolean ready,
            int currentMemberCount,
            List<GroupScheduleBlockingReason> blockingReasons,
            List<GroupScheduleMissingBookMemberResponse> missingBookMembers
    ) {
    }

    private record AssignmentKey(String roundId, String memberId) {
    }
}
