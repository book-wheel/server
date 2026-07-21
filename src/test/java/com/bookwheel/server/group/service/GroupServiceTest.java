package com.bookwheel.server.group.service;

import com.bookwheel.server.chat.repository.ChatRoomRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.dto.GroupCreateRequest;
import com.bookwheel.server.group.dto.GroupCreateResponse;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.schedule.repository.RoundRepository;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-16T03:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private GroupMemberPermissionValidator memberPermissionValidator;

    @Mock
    private RoundRepository roundRepository;

    @Mock
    private WheelStateRepository wheelStateRepository;

    private GroupService groupService;

    @BeforeEach
    void setUp() {
        groupService = new GroupService(
                groupRepository,
                chatRoomRepository,
                memberRepository,
                userRepository,
                passwordEncoder,
                eventPublisher,
                memberPermissionValidator,
                roundRepository,
                wheelStateRepository,
                FIXED_CLOCK
        );
    }

    @Test
    @DisplayName("삭제된 모임과 같은 이름으로 새 모임을 만들 수 있다")
    void createGroup_AllowsDeletedGroupName() {
        GroupCreateRequest request = groupCreateRequest(LocalDate.now(FIXED_CLOCK).plusDays(1));
        User user = activeUser();
        given(groupRepository.existsNotDeletedByGroupName(request.groupName(), State.DELETED)).willReturn(false);
        given(userRepository.findById("leader-user-pk")).willReturn(Optional.of(user));
        given(groupRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        GroupCreateResponse response = groupService.createGroup(request, "leader-user-pk");

        assertThat(response.groupId()).isNotBlank();
        then(groupRepository).should().existsNotDeletedByGroupName(request.groupName(), State.DELETED);
        then(groupRepository).should().save(any());
    }

    @Test
    @DisplayName("오늘 시작하는 모임은 생성할 수 없다")
    void createGroup_RejectsTodayStartDate() {
        GroupCreateRequest request = groupCreateRequest(LocalDate.now(FIXED_CLOCK));
        given(groupRepository.existsNotDeletedByGroupName(request.groupName(), State.DELETED)).willReturn(false);

        assertThatThrownBy(() -> groupService.createGroup(request, "leader-user-pk"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_SCHEDULE_START_DATE_NOT_FUTURE);

        then(userRepository).shouldHaveNoInteractions();
    }

    private GroupCreateRequest groupCreateRequest(LocalDate startDate) {
        return new GroupCreateRequest(
                "삭제된 모임 이름",
                "한줄소개",
                "규칙",
                true,
                null,
                false,
                null,
                7,
                startDate,
                5
        );
    }

    private User activeUser() {
        return User.builder()
                .loginId("leader-login")
                .password("password")
                .nickname("리더")
                .mail("leader@example.com")
                .isActive(true)
                .build();
    }
}
