package com.bookwheel.server.group.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberRole;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GroupMemberPermissionValidatorTest {

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private GroupMemberPermissionValidator validator;

    @Test
    void validateActiveMember_AllowsActiveMember() {
        String groupId = "group-1";
        String userPK = "member-user-pk";
        given(memberRepository.existsByGroup_GroupIdAndUser_IdAndMemberStatus(
                groupId,
                userPK,
                MemberStatus.ACTIVE
        )).willReturn(true);

        assertThatCode(() -> validator.validateActiveMember(groupId, userPK))
                .doesNotThrowAnyException();
    }

    @Test
    void validateActiveMember_RejectsNonActiveMember() {
        String groupId = "group-1";
        String userPK = "non-active-user-pk";
        given(memberRepository.existsByGroup_GroupIdAndUser_IdAndMemberStatus(
                groupId,
                userPK,
                MemberStatus.ACTIVE
        )).willReturn(false);

        assertThatThrownBy(() -> validator.validateActiveMember(groupId, userPK))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_ACTIVE_MEMBER_ONLY);
    }

    @Test
    void validateLeader_AllowsActiveLeader() {
        String groupId = "group-1";
        String userPK = "leader-user-pk";
        Member leader = member(MemberRole.LEADER, MemberStatus.ACTIVE);
        given(memberRepository.findByGroup_GroupIdAndUser_Id(groupId, userPK))
                .willReturn(Optional.of(leader));

        assertThatCode(() -> validator.validateLeader(groupId, userPK))
                .doesNotThrowAnyException();
    }

    @Test
    void validateLeader_RejectsRegularMember() {
        String groupId = "group-1";
        String userPK = "member-user-pk";
        Member member = member(MemberRole.MEMBER, MemberStatus.ACTIVE);
        given(memberRepository.findByGroup_GroupIdAndUser_Id(groupId, userPK))
                .willReturn(Optional.of(member));

        assertLeaderOnly(() -> validator.validateLeader(groupId, userPK));
    }

    @Test
    void validateLeader_RejectsActiveSubLeader() {
        String groupId = "group-1";
        String userPK = "sub-leader-user-pk";
        Member subLeader = member(MemberRole.SUB_LEADER, MemberStatus.ACTIVE);
        given(memberRepository.findByGroup_GroupIdAndUser_Id(groupId, userPK))
                .willReturn(Optional.of(subLeader));

        assertLeaderOnly(() -> validator.validateLeader(groupId, userPK));
    }

    @Test
    void validateLeader_RejectsInactiveLeader() {
        String groupId = "group-1";
        String userPK = "inactive-leader-user-pk";
        Member leader = member(MemberRole.LEADER, MemberStatus.EXITED);
        given(memberRepository.findByGroup_GroupIdAndUser_Id(groupId, userPK))
                .willReturn(Optional.of(leader));

        assertLeaderOnly(() -> validator.validateLeader(groupId, userPK));
    }

    @Test
    void validateLeader_RejectsUnknownMember() {
        String groupId = "group-1";
        String userPK = "unknown-user-pk";
        given(memberRepository.findByGroup_GroupIdAndUser_Id(groupId, userPK))
                .willReturn(Optional.empty());

        assertLeaderOnly(() -> validator.validateLeader(groupId, userPK));
    }

    @Test
    void validateManager_AllowsActiveLeader() {
        assertManagerAllowed(MemberRole.LEADER, "leader-user-pk");
    }

    @Test
    void validateManager_AllowsActiveSubLeader() {
        assertManagerAllowed(MemberRole.SUB_LEADER, "sub-leader-user-pk");
    }

    @Test
    void validateManager_RejectsRegularMember() {
        assertManagerOnly(MemberRole.MEMBER, MemberStatus.ACTIVE, "member-user-pk");
    }

    @Test
    void validateManager_RejectsInactiveSubLeader() {
        assertManagerOnly(MemberRole.SUB_LEADER, MemberStatus.EXITED, "inactive-sub-leader-user-pk");
    }

    @Test
    void validateManager_RejectsUnknownMember() {
        String groupId = "group-1";
        String userPK = "unknown-user-pk";
        given(memberRepository.findByGroup_GroupIdAndUser_Id(groupId, userPK))
                .willReturn(Optional.empty());

        assertManagerOnly(() -> validator.validateManager(groupId, userPK));
    }

    private Member member(MemberRole role, MemberStatus status) {
        return Member.builder()
                .memberRole(role)
                .memberStatus(status)
                .build();
    }

    private void assertManagerAllowed(MemberRole role, String userPK) {
        String groupId = "group-1";
        given(memberRepository.findByGroup_GroupIdAndUser_Id(groupId, userPK))
                .willReturn(Optional.of(member(role, MemberStatus.ACTIVE)));

        assertThatCode(() -> validator.validateManager(groupId, userPK))
                .doesNotThrowAnyException();
    }

    private void assertManagerOnly(MemberRole role, MemberStatus status, String userPK) {
        String groupId = "group-1";
        given(memberRepository.findByGroup_GroupIdAndUser_Id(groupId, userPK))
                .willReturn(Optional.of(member(role, status)));

        assertManagerOnly(() -> validator.validateManager(groupId, userPK));
    }

    private void assertLeaderOnly(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_LEADER_ONLY);
    }

    private void assertManagerOnly(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_MANAGER_ONLY);
    }
}
