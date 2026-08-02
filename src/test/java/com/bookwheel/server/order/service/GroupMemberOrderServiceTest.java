package com.bookwheel.server.order.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.group.repository.GroupRepository;
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
    @DisplayName("시작 후에는 확정된 배정과 달라질 수 있으므로 읽기 순서를 변경할 수 없다")
    void assignReadOrder_RejectsInProgressGroup() {
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
                                .isEqualTo(ErrorCode.GROUP_RECRUITING_STATE_REQUIRED)
                );
        then(memberRepository).shouldHaveNoInteractions();
        then(recruitingScheduleAssignmentService).shouldHaveNoInteractions();
    }
}
