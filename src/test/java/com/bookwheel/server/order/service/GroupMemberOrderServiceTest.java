package com.bookwheel.server.order.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.ScheduleReconfigurationStatus;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberRole;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.order.dto.MemberReadOrderRequest;
import com.bookwheel.server.schedule.service.RecruitingScheduleAssignmentService;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class GroupMemberOrderServiceTest {
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private RecruitingScheduleAssignmentService recruitingScheduleAssignmentService;

    private GroupMemberOrderService service;

    @BeforeEach
    void setUp() {
        service = new GroupMemberOrderService(
                groupRepository,
                memberRepository,
                userRepository,
                eventPublisher,
                recruitingScheduleAssignmentService
        );
    }

    @Test
    @DisplayName("재확인이 필요하지 않은 진행 중 모임은 읽기 순서를 변경할 수 없다")
    void assignReadOrder_RejectsInProgressGroupWithoutReconfirmationRequest() {
        String groupId = "group-1";
        User user = User.builder()
                .loginId("leader")
                .password("password")
                .nickname("리더")
                .mail("leader@example.com")
                .isActive(true)
                .build();
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("독서 모임")
                .groupState(State.IN_PROGRESS)
                .build();
        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));

        MemberReadOrderRequest request = new MemberReadOrderRequest(false, List.of("member-1", "member-2"));

        assertThatThrownBy(() -> service.assignReadOrder(groupId, request, user.getId()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GROUP_READ_ORDER_RECONFIRMATION_NOT_ALLOWED)
                );
        then(memberRepository).shouldHaveNoInteractions();
        then(recruitingScheduleAssignmentService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("진행 중 재확인 상태에서는 기존 멤버 순서를 다시 지정할 수 있다")
    void assignReadOrder_AllowsInProgressReconfirmation() {
        String groupId = "group-1";
        User leaderUser = activeUser("leader", "리더");
        User memberUser = activeUser("member", "멤버");
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("독서 모임")
                .groupState(State.IN_PROGRESS)
                .scheduleReconfigurationStatus(ScheduleReconfigurationStatus.READ_ORDER_CONFIRMATION_REQUIRED)
                .build();
        Member leader = activeMember("member-1", group, leaderUser, MemberRole.LEADER);
        Member member = activeMember("member-2", group, memberUser, MemberRole.MEMBER);

        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById(leaderUser.getId())).willReturn(Optional.of(leaderUser));
        given(memberRepository.findByGroup_GroupIdAndUser_Id(groupId, leaderUser.getId()))
                .willReturn(Optional.of(leader));
        given(memberRepository.findByGroupIdAndMemberStatusForUpdate(groupId, MemberStatus.ACTIVE))
                .willReturn(List.of(leader, member));

        var response = service.assignReadOrder(
                groupId,
                new MemberReadOrderRequest(false, List.of(member.getMemberId(), leader.getMemberId())),
                leaderUser.getId()
        );

        assertThat(response).extracting(item -> item.memberId())
                .containsExactly(member.getMemberId(), leader.getMemberId());
        assertThat(member.getReadOrder()).isEqualTo(1);
        assertThat(leader.getReadOrder()).isEqualTo(2);
        assertThat(group.getScheduleReconfigurationStatus())
                .isEqualTo(ScheduleReconfigurationStatus.FUTURE_SCHEDULE_CONFIRMATION_REQUIRED);
        then(recruitingScheduleAssignmentService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("미래 일정 확인 단계에서도 읽기 순서를 다시 수정할 수 있다")
    void assignReadOrder_AllowsEditingAgainBeforeFutureScheduleConfirmation() {
        String groupId = "group-1";
        User leaderUser = activeUser("leader", "리더");
        User memberUser = activeUser("member", "멤버");
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("독서 모임")
                .groupState(State.IN_PROGRESS)
                .scheduleReconfigurationStatus(ScheduleReconfigurationStatus.FUTURE_SCHEDULE_CONFIRMATION_REQUIRED)
                .build();
        Member leader = activeMember("member-1", group, leaderUser, MemberRole.LEADER);
        Member member = activeMember("member-2", group, memberUser, MemberRole.MEMBER);

        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById(leaderUser.getId())).willReturn(Optional.of(leaderUser));
        given(memberRepository.findByGroup_GroupIdAndUser_Id(groupId, leaderUser.getId()))
                .willReturn(Optional.of(leader));
        given(memberRepository.findByGroupIdAndMemberStatusForUpdate(groupId, MemberStatus.ACTIVE))
                .willReturn(List.of(leader, member));

        service.assignReadOrder(
                groupId,
                new MemberReadOrderRequest(false, List.of(leader.getMemberId(), member.getMemberId())),
                leaderUser.getId()
        );

        assertThat(group.getScheduleReconfigurationStatus())
                .isEqualTo(ScheduleReconfigurationStatus.FUTURE_SCHEDULE_CONFIRMATION_REQUIRED);
    }

    private User activeUser(String loginId, String nickname) {
        return User.builder()
                .loginId(loginId)
                .password("password")
                .nickname(nickname)
                .mail(loginId + "@example.com")
                .isActive(true)
                .build();
    }

    private Member activeMember(String memberId, Group group, User user, MemberRole role) {
        return Member.builder()
                .memberId(memberId)
                .group(group)
                .user(user)
                .memberRole(role)
                .memberStatus(MemberStatus.ACTIVE)
                .build();
    }
}
