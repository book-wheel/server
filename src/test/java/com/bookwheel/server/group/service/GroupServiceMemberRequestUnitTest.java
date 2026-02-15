package com.bookwheel.server.group.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.dto.MemberRequestResponse;
import com.bookwheel.server.group.dto.MemberRequestStatus;
import com.bookwheel.server.group.dto.MemberRequestStatusUpdateResponse;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberRole;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.user.entity.Role;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceMemberRequestUnitTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GroupService groupService;

    @Test
    @DisplayName("Leader can get pending member requests")
    void getMemberRequests_leader_success() {
        Group group = createGroup("group-1", 5, 1);
        Member leader = createMember("leader-member", group, createUser("leader"), MemberRole.LEADER, MemberStatus.ACTIVE);
        Member pending = createMember("pending-member", group, createUser("member"), MemberRole.MEMBER, MemberStatus.PENDING);
        LocalDateTime requestDate = LocalDateTime.of(2026, 1, 26, 10, 0);
        pending.setRequestDate(requestDate);

        when(groupRepository.findById("group-1")).thenReturn(Optional.of(group));
        when(memberRepository.findByGroup_GroupIdAndUser_UserId("group-1", "leader")).thenReturn(Optional.of(leader));
        when(memberRepository.findByGroup_GroupIdAndMemberStatus("group-1", MemberStatus.PENDING))
                .thenReturn(List.of(pending));

        List<MemberRequestResponse> result = groupService.getMemberRequests("group-1", "leader");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).memberId()).isEqualTo("pending-member");
        assertThat(result.get(0).requestDate()).isEqualTo(requestDate);
        assertThat(result.get(0).status()).isEqualTo(MemberStatus.PENDING);
    }

    @Test
    @DisplayName("Non leader cannot get pending member requests")
    void getMemberRequests_nonLeader_fail() {
        Group group = createGroup("group-1", 5, 1);
        Member notLeader = createMember("normal-member", group, createUser("normal"), MemberRole.MEMBER, MemberStatus.ACTIVE);

        when(groupRepository.findById("group-1")).thenReturn(Optional.of(group));
        when(memberRepository.findByGroup_GroupIdAndUser_UserId("group-1", "normal")).thenReturn(Optional.of(notLeader));

        assertThatThrownBy(() -> groupService.getMemberRequests("group-1", "normal"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.GROUP_LEADER_ONLY);
    }

    @Test
    @DisplayName("Approve changes PENDING to ACTIVE")
    void updateMemberRequestStatus_approved_success() {
        Group group = createGroup("group-1", 5, 1);
        Member leader = createMember("leader-member", group, createUser("leader"), MemberRole.LEADER, MemberStatus.ACTIVE);
        Member pending = createMember("pending-member", group, createUser("member"), MemberRole.MEMBER, MemberStatus.PENDING);

        when(groupRepository.findById("group-1")).thenReturn(Optional.of(group));
        when(memberRepository.findByGroup_GroupIdAndUser_UserId("group-1", "leader")).thenReturn(Optional.of(leader));
        when(memberRepository.findByMemberIdAndGroup_GroupId("pending-member", "group-1")).thenReturn(Optional.of(pending));

        MemberRequestStatusUpdateResponse response = groupService.updateMemberRequestStatus(
                "group-1", "pending-member", "leader", MemberRequestStatus.APPROVED
        );

        assertThat(pending.getMemberStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(response.memberId()).isEqualTo("pending-member");
        assertThat(response.status()).isEqualTo(MemberRequestStatus.APPROVED);
    }

    @Test
    @DisplayName("Reject changes PENDING to REJECTED")
    void updateMemberRequestStatus_rejected_success() {
        Group group = createGroup("group-1", 5, 1);
        Member leader = createMember("leader-member", group, createUser("leader"), MemberRole.LEADER, MemberStatus.ACTIVE);
        Member pending = createMember("pending-member", group, createUser("member"), MemberRole.MEMBER, MemberStatus.PENDING);

        when(groupRepository.findById("group-1")).thenReturn(Optional.of(group));
        when(memberRepository.findByGroup_GroupIdAndUser_UserId("group-1", "leader")).thenReturn(Optional.of(leader));
        when(memberRepository.findByMemberIdAndGroup_GroupId("pending-member", "group-1")).thenReturn(Optional.of(pending));

        MemberRequestStatusUpdateResponse response = groupService.updateMemberRequestStatus(
                "group-1", "pending-member", "leader", MemberRequestStatus.REJECTED
        );

        assertThat(pending.getMemberStatus()).isEqualTo(MemberStatus.REJECTED);
        assertThat(response.status()).isEqualTo(MemberRequestStatus.REJECTED);
    }

    @Test
    @DisplayName("Only PENDING member can be handled")
    void updateMemberRequestStatus_nonPending_fail() {
        Group group = createGroup("group-1", 5, 1);
        Member leader = createMember("leader-member", group, createUser("leader"), MemberRole.LEADER, MemberStatus.ACTIVE);
        Member activeMember = createMember("active-member", group, createUser("member"), MemberRole.MEMBER, MemberStatus.ACTIVE);

        when(groupRepository.findById("group-1")).thenReturn(Optional.of(group));
        when(memberRepository.findByGroup_GroupIdAndUser_UserId("group-1", "leader")).thenReturn(Optional.of(leader));
        when(memberRepository.findByMemberIdAndGroup_GroupId("active-member", "group-1")).thenReturn(Optional.of(activeMember));

        assertThatThrownBy(() -> groupService.updateMemberRequestStatus(
                "group-1", "active-member", "leader", MemberRequestStatus.APPROVED
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_REQUEST_NOT_PENDING);
    }

    @Test
    @DisplayName("Approve fails when group is full")
    void updateMemberRequestStatus_groupFull_fail() {
        Group group = createGroup("group-1", 2, 2);
        Member leader = createMember("leader-member", group, createUser("leader"), MemberRole.LEADER, MemberStatus.ACTIVE);
        Member pending = createMember("pending-member", group, createUser("member"), MemberRole.MEMBER, MemberStatus.PENDING);

        when(groupRepository.findById("group-1")).thenReturn(Optional.of(group));
        when(memberRepository.findByGroup_GroupIdAndUser_UserId("group-1", "leader")).thenReturn(Optional.of(leader));
        when(memberRepository.findByMemberIdAndGroup_GroupId("pending-member", "group-1")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> groupService.updateMemberRequestStatus(
                "group-1", "pending-member", "leader", MemberRequestStatus.APPROVED
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.GROUP_FULL);
    }

    private Group createGroup(String groupId, int maxMembers, int currentMembers) {
        return Group.builder()
                .groupId(groupId)
                .groupName("test-group")
                .maxMembers(maxMembers)
                .currentMembers(currentMembers)
                .build();
    }

    private User createUser(String userId) {
        return User.builder()
                .userId(userId)
                .password("encoded-password")
                .nickname(userId + "-nickname")
                .mail(userId + "@mail.com")
                .role(Role.USER)
                .build();
    }

    private Member createMember(
            String memberId,
            Group group,
            User user,
            MemberRole role,
            MemberStatus status
    ) {
        return Member.builder()
                .memberId(memberId)
                .group(group)
                .user(user)
                .memberRole(role)
                .memberStatus(status)
                .build();
    }
}
