package com.bookwheel.server.group.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.dto.setting.MemberKickResponse;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupSettingService {
    private final MemberRepository memberRepository;
    private final GroupMemberPermissionValidator memberPermissionValidator;

    @Transactional
    public MemberKickResponse kickMember(String groupId, String memberId, String leaderUserPk) {
        memberPermissionValidator.validateLeader(groupId, leaderUserPk);

        Member targetMember = memberRepository.findByMemberIdAndGroup_GroupId(memberId, groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        targetMember.kick();

        return MemberKickResponse.of(targetMember.getMemberId(), targetMember.getMemberStatus());
    }
}
