package com.bookwheel.server.group.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.dto.setting.LeadershipTransferResponse;
import com.bookwheel.server.group.dto.setting.MemberExitResponse;
import com.bookwheel.server.group.dto.setting.MemberKickResponse;
import com.bookwheel.server.group.dto.setting.MemberRoleChangeResponse;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberRole;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.schedule.entity.Round;
import com.bookwheel.server.schedule.repository.RoundRepository;
import com.bookwheel.server.wheel.entity.WheelState;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupSettingService {
    private final MemberRepository memberRepository;
    private final RoundRepository roundRepository;
    private final WheelStateRepository wheelStateRepository;
    private final GroupMemberPermissionValidator memberPermissionValidator;

    @Transactional
    public MemberKickResponse kickMember(String groupId, String targetUserPK, String leaderUserPK) {
        memberPermissionValidator.validateLeader(groupId, leaderUserPK);

        if (leaderUserPK.equals(targetUserPK)) {
            throw new BusinessException(ErrorCode.CANNOT_KICK_YOURSELF);
        }

        Member targetMember = memberRepository.findByGroup_GroupIdAndUser_Id(groupId, targetUserPK)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        targetMember.kick();

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
        Member member = memberRepository.findByGroup_GroupIdAndUser_Id(groupId, userPK)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // 1. 그룹장/부그룹장은 중도하차 불가 — 권한 위임/해제 선행 필요
        MemberRole role = member.getMemberRole();
        if (role == MemberRole.LEADER || role == MemberRole.SUB_LEADER) {
            throw new BusinessException(ErrorCode.LEADER_CANNOT_EXIT);
        }

        // 2. 현재 라운드의 책이 완독 처리되지 않았다면 중도하차 불가
        Round currentRound = roundRepository.findCurrentRound(groupId, LocalDate.now())
                .orElse(null);

        if (currentRound != null) {
            WheelState myWheelState = wheelStateRepository
                    .findFirstByRoundIdAndMember_MemberId(currentRound.getRoundId(), member.getMemberId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.READING_NOT_COMPLETED));

            if (!Boolean.TRUE.equals(myWheelState.getIsCompleted())) {
                throw new BusinessException(ErrorCode.READING_NOT_COMPLETED);
            }
        }

        member.exit();

        return MemberExitResponse.of(groupId, userPK, member.getMemberStatus());
    }
}
