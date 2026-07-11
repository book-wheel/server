package com.bookwheel.server.group.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.dto.setting.LeadershipTransferResponse;
import com.bookwheel.server.group.dto.setting.MemberExitResponse;
import com.bookwheel.server.group.dto.setting.MemberKickResponse;
import com.bookwheel.server.group.dto.setting.MemberRoleChangeResponse;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberRole;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.schedule.entity.Round;
import com.bookwheel.server.schedule.repository.RoundRepository;
import com.bookwheel.server.wheel.entity.WheelState;
import com.bookwheel.server.wheel.dto.WheelAssignmentPlan;
import com.bookwheel.server.wheel.enums.WheelStatus;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import com.bookwheel.server.wheel.service.WheelReassignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupSettingService {
    private final GroupRepository groupRepository;
    private final MemberRepository memberRepository;
    private final RoundRepository roundRepository;
    private final WheelStateRepository wheelStateRepository;
    private final WheelReassignmentService wheelReassignmentService;
    private final GroupMemberPermissionValidator memberPermissionValidator;
    private final Clock clock;

    @Transactional
    public MemberKickResponse kickMember(String groupId, String targetUserPK, String leaderUserPK) {
        if (leaderUserPK.equals(targetUserPK)) {
            throw new BusinessException(ErrorCode.CANNOT_KICK_YOURSELF);
        }

        // 멤버 구성과 라운드 재배정 판단을 하나의 잠긴 시점에서 처리한다.
        LockedActiveMembers lockedMembers = lockedActiveMembers(groupId);
        List<Member> activeMembers = lockedMembers.activeMembers();

        memberPermissionValidator.validateLeader(groupId, leaderUserPK);

        Member targetMember = findKickTargetMember(groupId, activeMembers, targetUserPK);
        validateKickableTarget(targetMember);
        List<Member> remainingMembers = remainingMembers(activeMembers, targetMember);

        Runnable postStatusMutation = prepareRemovalBeforeStatusMutation(
                lockedMembers.group(),
                targetMember,
                remainingMembers,
                false
        );
        targetMember.kick();
        postStatusMutation.run();

        return MemberKickResponse.of(targetUserPK, targetMember.getMemberStatus());
    }


    @Transactional
    public MemberRoleChangeResponse changeRole(String groupId, String targetUserPK, String leaderUserPK, MemberRole role) {
        // 부방장 권한 관련 내용만 다룸
        if (role == MemberRole.LEADER) {
            throw new BusinessException(ErrorCode.INVALID_ROLE_TRANSITION);
        }
        // 멤버 혹은 부방장만 다룸
        if (role != MemberRole.MEMBER && role != MemberRole.SUB_LEADER) {
            throw new BusinessException(ErrorCode.INVALID_ROLE_TRANSITION);
        }
        // 요청자와 타겟은 동일하게 X
        if (leaderUserPK.equals(targetUserPK)) {
            throw new BusinessException(ErrorCode.CANNOT_CHANGE_OWN_ROLE);
        }

        // 요청자가 방장인지 권한 검증
        memberPermissionValidator.validateLeader(groupId, leaderUserPK);

        // 타겟 멤버가 그룹에 속하는지 확인
        Member targetMember = memberRepository.findByGroup_GroupIdAndUser_Id(groupId, targetUserPK)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // 타겟 멤버의 현재 권한 검증
        MemberRole currentRole = targetMember.getMemberRole();

        if (currentRole == MemberRole.LEADER || currentRole == MemberRole.OUT) {
            throw new BusinessException(ErrorCode.INVALID_ROLE_CHANGE);
        }
        if (currentRole == role) {
            throw new BusinessException(ErrorCode.ROLE_ALREADY_ASSIGNED);
        }

        // 권한 변경
        if (currentRole == MemberRole.MEMBER) {
            targetMember.promoteToSubLeader();
        } else {
            targetMember.demoteToMember();
        }

        return MemberRoleChangeResponse.of(groupId, targetUserPK, targetMember.getMemberRole());
    }

    @Transactional
    public LeadershipTransferResponse transferLeadership(String groupId, String targetUserPK, String leaderUserPK) {
        if (leaderUserPK.equals(targetUserPK)) {
            throw new BusinessException(ErrorCode.CANNOT_TRANSFER_TO_SELF);
        }

        memberPermissionValidator.validateLeader(groupId, leaderUserPK);

        Member leaderMember = memberRepository.findByGroup_GroupIdAndUser_Id(groupId, leaderUserPK)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        Member targetMember = memberRepository.findByGroup_GroupIdAndUser_Id(groupId, targetUserPK)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // ACTIVE 아닌 멤버에게는 위임 불가
        if (targetMember.getMemberStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_TARGET_MEMBER);
        }

        MemberRole targetCurrentRole = targetMember.getMemberRole();
        // 타겟 유저의 권한이 이상하면 오류 발생
        if (targetCurrentRole != MemberRole.MEMBER && targetCurrentRole != MemberRole.SUB_LEADER) {
            throw new BusinessException(ErrorCode.INVALID_TARGET_MEMBER);
        }

        // 방장으로 위임 후 일반 멤버로 돌아가기
        leaderMember.transferLeaderTo(targetMember);

        return LeadershipTransferResponse.of(
                groupId,
                targetUserPK,
                targetMember.getMemberRole(),
                leaderUserPK,
                leaderMember.getMemberRole()
        );
    }

    @Transactional
    public MemberExitResponse exitMember(String groupId, String userPK) {
        LockedActiveMembers lockedMembers = lockedActiveMembers(groupId);
        List<Member> activeMembers = lockedMembers.activeMembers();
        Member member = findActiveMember(activeMembers, userPK);

        // 1. 그룹장/부그룹장은 중도하차 불가 — 권한 위임/해제 선행 필요
        MemberRole role = member.getMemberRole();
        if (role == MemberRole.LEADER || role == MemberRole.SUB_LEADER) {
            throw new BusinessException(ErrorCode.LEADER_CANNOT_EXIT);
        }

        List<Member> remainingMembers = remainingMembers(activeMembers, member);
        Runnable postStatusMutation = prepareRemovalBeforeStatusMutation(
                lockedMembers.group(),
                member,
                remainingMembers,
                true
        );

        member.exit();
        postStatusMutation.run();

        return MemberExitResponse.of(groupId, userPK, member.getMemberStatus());
    }

    private LockedActiveMembers lockedActiveMembers(String groupId) {
        Group group = groupRepository.findByGroupIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
        List<Member> activeMembers = memberRepository.findByGroupIdAndMemberStatusForUpdate(groupId, MemberStatus.ACTIVE);
        return new LockedActiveMembers(group, activeMembers);
    }

    private Runnable prepareRemovalBeforeStatusMutation(
            Group group,
            Member member,
            List<Member> remainingMembers,
            boolean validateCurrentReading
    ) {
        switch (group.getGroupState()) {
            case IN_PROGRESS -> {
                // 현재/완료 라운드는 보존하고, 미래 라운드만 모두 재배정 가능할 때 탈퇴를 허용한다.
                if (validateCurrentReading) {
                    validateCurrentRoundCompletion(group.getGroupId(), member);
                }
                try {
                    WheelAssignmentPlan plan = wheelReassignmentService.reassignFutureRounds(
                            group.getGroupId(),
                            member,
                            remainingMembers
                    );
                    return () -> wheelReassignmentService.replaceFuturePlannedAssignments(
                            group.getGroupId(),
                            plan,
                            remainingMembers
                    );
                } catch (BusinessException exception) {
                    if (exception.getErrorCode() != ErrorCode.WHEEL_REASSIGNMENT_IMPOSSIBLE) {
                        throw exception;
                    }
                    return () -> deleteFutureRoundsForManualRegeneration(group);
                }
            }
            // 모집 중에 바뀐 멤버 구성은 다음 일정 생성 시점에 다시 반영한다.
            case RECRUITING -> {
                invalidateGeneratedScheduleIfPresent(group);
                return () -> {
                };
            }
            case COMPLETE -> {
                return () -> {
                };
            }
        }
        throw new IllegalStateException("Unsupported group state: " + group.getGroupState());
    }

    private void deleteFutureRoundsForManualRegeneration(Group group) {
        LocalDate today = LocalDate.now(clock);
        List<Round> rounds = roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(group.getGroupId());
        List<Round> futureRounds = rounds.stream()
                .filter(round -> isFutureRound(round, today))
                .toList();
        if (futureRounds.isEmpty()) {
            return;
        }

        List<String> futureRoundIds = futureRounds.stream()
                .map(Round::getRoundId)
                .toList();
        int lastProtectedRoundNumber = rounds.stream()
                .filter(round -> !isFutureRound(round, today))
                .map(Round::getRoundNumber)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);

        wheelStateRepository.deleteByRoundIdIn(futureRoundIds);
        roundRepository.deleteAllByIdInBatch(futureRoundIds);
        group.updateScheduleInfo(group.getStartDate(), lastProtectedRoundNumber);
    }

    private boolean isFutureRound(Round round, LocalDate today) {
        return round.getStartDate() != null && round.getStartDate().isAfter(today);
    }

    private void invalidateGeneratedScheduleIfPresent(Group group) {
        List<Round> rounds = roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(group.getGroupId());
        if (rounds.isEmpty()) {
            return;
        }

        List<String> roundIds = rounds.stream().map(Round::getRoundId).toList();
        List<WheelState> wheelStates = wheelStateRepository.findByRoundIdIn(roundIds);
        boolean hasStartedWheelState = wheelStates.stream()
                .anyMatch(wheelState -> wheelState.getWheelState() != WheelStatus.PLANNED);
        if (hasStartedWheelState) {
            // 이미 생성된 진행 기록은 모집 일정 무효화로 지우지 않는다.
            throw new BusinessException(ErrorCode.GROUP_SCHEDULE_INVALIDATION_BLOCKED_BY_WHEEL_STATE);
        }

        wheelStateRepository.deleteByRoundIdInAndWheelState(roundIds, WheelStatus.PLANNED);
        roundRepository.deleteByGroup_GroupId(group.getGroupId());
        group.invalidateSchedule();
    }

    private Member findActiveMember(List<Member> activeMembers, String userPK) {
        return activeMembers.stream()
                .filter(member -> member.getUser().getId().equals(userPK))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private Member findKickTargetMember(String groupId, List<Member> activeMembers, String targetUserPK) {
        return activeMembers.stream()
                .filter(member -> member.getUser().getId().equals(targetUserPK))
                .findFirst()
                .or(() -> memberRepository.findByGroup_GroupIdAndUser_Id(groupId, targetUserPK))
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private void validateKickableTarget(Member targetMember) {
        if (targetMember.getMemberStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_TARGET_MEMBER);
        }
        if (targetMember.getMemberRole() != MemberRole.MEMBER) {
            throw new BusinessException(ErrorCode.GROUP_MEMBER_ONLY);
        }
    }

    private List<Member> remainingMembers(List<Member> activeMembers, Member targetMember) {
        return activeMembers.stream()
                .filter(member -> !member.getMemberId().equals(targetMember.getMemberId()))
                .toList();
    }

    private void validateCurrentRoundCompletion(String groupId, Member member) {
        Round currentRound = roundRepository.findCurrentRound(groupId, LocalDate.now(clock))
                .orElse(null);

        if (currentRound != null) {
            WheelState myWheelState = wheelStateRepository
                    .findFirstByRoundIdAndMember_MemberId(currentRound.getRoundId(), member.getMemberId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.READING_NOT_COMPLETED));

            if (!Boolean.TRUE.equals(myWheelState.getIsCompleted())) {
                throw new BusinessException(ErrorCode.READING_NOT_COMPLETED);
            }
        }
    }

    private record LockedActiveMembers(Group group, List<Member> activeMembers) {
    }
}
