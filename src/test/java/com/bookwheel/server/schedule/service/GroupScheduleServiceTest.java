package com.bookwheel.server.schedule.service;

import com.bookwheel.server.book.repository.OwnBookRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.group.service.GroupMemberPermissionValidator;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.schedule.dto.GroupScheduleCreateRequest;
import com.bookwheel.server.schedule.repository.RoundRepository;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import com.bookwheel.server.wheel.service.WheelAssignmentService;
import com.bookwheel.server.wheel.service.WheelReassignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class GroupScheduleServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-16T03:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OwnBookRepository ownBookRepository;

    @Mock
    private RoundRepository roundRepository;

    @Mock
    private WheelStateRepository wheelStateRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private GroupMemberPermissionValidator memberPermissionValidator;

    @Mock
    private WheelAssignmentService wheelAssignmentService;

    @Mock
    private WheelReassignmentService wheelReassignmentService;

    @Mock
    private FutureScheduleService futureScheduleService;

    @Mock
    private ScheduleCalendarService scheduleCalendarService;

    private GroupScheduleService groupScheduleService;

    @BeforeEach
    void setUp() {
        groupScheduleService = new GroupScheduleService(
                groupRepository,
                memberRepository,
                userRepository,
                ownBookRepository,
                roundRepository,
                wheelStateRepository,
                eventPublisher,
                memberPermissionValidator,
                wheelAssignmentService,
                wheelReassignmentService,
                futureScheduleService,
                scheduleCalendarService,
                FIXED_CLOCK
        );
    }

    @Test
    @DisplayName("오늘 시작하는 독서 일정은 생성할 수 없다")
    void createSchedule_RejectsTodayStartDate() {
        String groupId = "group-1";
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(State.RECRUITING)
                .build();
        GroupScheduleCreateRequest request = new GroupScheduleCreateRequest(
                LocalDate.now(FIXED_CLOCK),
                7,
                null,
                List.of(),
                List.of()
        );
        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById("leader-user-pk")).willReturn(Optional.of(activeUser()));

        assertThatThrownBy(() -> groupScheduleService.createSchedule(groupId, request, "leader-user-pk"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_SCHEDULE_START_DATE_NOT_FUTURE);

        then(memberPermissionValidator).should().validateLeader(groupId, "leader-user-pk");
        then(memberRepository).shouldHaveNoInteractions();
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
