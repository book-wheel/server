package com.bookwheel.server.schedule.service;

import com.bookwheel.server.book.entity.OwnBook;
import com.bookwheel.server.book.repository.OwnBookRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.group.service.GroupMemberPermissionValidator;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.schedule.dto.GroupScheduleCreateRequest;
import com.bookwheel.server.schedule.dto.GroupScheduleRoundResponse;
import com.bookwheel.server.schedule.entity.Round;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

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

    @Test
    @DisplayName("멤버가 1명이어도 도서 검증 없이 일정 설정을 저장한다")
    void createSchedule_SavesSettingsWithOneMember() {
        String groupId = "group-1";
        LocalDate startDate = LocalDate.now(FIXED_CLOCK).plusDays(3);
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(State.RECRUITING)
                .build();
        GroupScheduleCreateRequest request = new GroupScheduleCreateRequest(
                startDate,
                5,
                startDate.plusDays(30),
                List.of(),
                List.of()
        );
        ScheduleCalendarService.ExcludedCalendar excludedCalendar = mock(ScheduleCalendarService.ExcludedCalendar.class);
        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById("leader-user-pk")).willReturn(Optional.of(activeUser()));
        given(memberRepository.findByGroup_GroupIdAndMemberStatus(groupId, com.bookwheel.server.member.enums.MemberStatus.ACTIVE))
                .willReturn(List.of(mock(Member.class)));
        given(scheduleCalendarService.normalizeExcludedCalendar(List.of(), List.of())).willReturn(excludedCalendar);
        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId)).willReturn(List.of());

        List<GroupScheduleRoundResponse> response =
                groupScheduleService.createSchedule(groupId, request, "leader-user-pk");

        assertThat(response).isEmpty();
        assertThat(group.getStartDate()).isEqualTo(startDate);
        assertThat(group.getReadingPeriod()).isEqualTo(5);
        assertThat(group.getGroupRoundCount()).isZero();
        assertThat(group.getScheduleEndDate()).isEqualTo(startDate.plusDays(30));
        then(roundRepository).should(never()).saveAll(anyList());
        then(ownBookRepository).shouldHaveNoInteractions();
        then(wheelAssignmentService).shouldHaveNoInteractions();
        then(wheelReassignmentService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("일정 API는 라운드 날짜만 저장하고 책바퀴를 배정하지 않는다")
    void createSchedule_CreatesRoundsWithoutWheelAssignments() {
        String groupId = "group-1";
        LocalDate startDate = LocalDate.now(FIXED_CLOCK).plusDays(3);
        LocalDate endDate = startDate.plusDays(6);
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(State.RECRUITING)
                .build();
        GroupScheduleCreateRequest request = new GroupScheduleCreateRequest(
                startDate,
                7,
                null,
                List.of(),
                List.of()
        );
        List<Member> members = List.of(mock(Member.class), mock(Member.class));
        ScheduleCalendarService.ExcludedCalendar excludedCalendar = mock(ScheduleCalendarService.ExcludedCalendar.class);
        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById("leader-user-pk")).willReturn(Optional.of(activeUser()));
        given(memberRepository.findByGroup_GroupIdAndMemberStatus(groupId, com.bookwheel.server.member.enums.MemberStatus.ACTIVE))
                .willReturn(members);
        given(scheduleCalendarService.normalizeExcludedCalendar(List.of(), List.of())).willReturn(excludedCalendar);
        given(scheduleCalendarService.calculateRoundEndDate(startDate, 7, excludedCalendar)).willReturn(endDate);
        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId)).willReturn(List.of());

        List<GroupScheduleRoundResponse> response =
                groupScheduleService.createSchedule(groupId, request, "leader-user-pk");

        assertThat(response).containsExactly(GroupScheduleRoundResponse.of(1, startDate, endDate));
        then(roundRepository).should().saveAll(anyList());
        then(ownBookRepository).shouldHaveNoInteractions();
        then(wheelAssignmentService).shouldHaveNoInteractions();
        then(wheelReassignmentService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("시작일에 일정이 없으면 자동 생성하고 최종 멤버와 도서로 배정한다")
    void updateStartedGroups_CreatesMissingScheduleAndAssignments() {
        String groupId = "group-1";
        LocalDate today = LocalDate.now(FIXED_CLOCK);
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(State.RECRUITING)
                .startDate(today)
                .readingPeriod(7)
                .build();
        Member firstMember = mock(Member.class);
        Member secondMember = mock(Member.class);
        OwnBook firstBook = mock(OwnBook.class);
        OwnBook secondBook = mock(OwnBook.class);
        List<Member> members = List.of(firstMember, secondMember);
        List<OwnBook> books = List.of(firstBook, secondBook);
        given(firstMember.getMemberId()).willReturn("member-1");
        given(secondMember.getMemberId()).willReturn("member-2");
        given(firstBook.getOwnBookId()).willReturn("book-1");
        given(secondBook.getOwnBookId()).willReturn("book-2");
        given(groupRepository.findByGroupStateAndStartDateLessThanEqual(State.RECRUITING, today))
                .willReturn(List.of(group));
        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(memberRepository.findByGroupIdAndMemberStatusForUpdate(
                groupId,
                com.bookwheel.server.member.enums.MemberStatus.ACTIVE
        )).willReturn(members);
        given(ownBookRepository.findByGroup_GroupIdIn(List.of(groupId))).willReturn(books);
        given(wheelAssignmentService.findMembersWithoutBook(members, books)).willReturn(List.of());
        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId)).willReturn(List.of());
        given(scheduleCalendarService.normalizeExcludedCalendar(List.of(), List.of()))
                .willReturn(mock(ScheduleCalendarService.ExcludedCalendar.class));
        given(scheduleCalendarService.calculateRoundEndDate(
                org.mockito.ArgumentMatchers.eq(today),
                org.mockito.ArgumentMatchers.eq(7),
                org.mockito.ArgumentMatchers.any(ScheduleCalendarService.ExcludedCalendar.class)
        )).willReturn(today.plusDays(6));
        given(roundRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));
        given(wheelStateRepository.findByRoundIdIn(anyList())).willReturn(List.of());
        given(wheelAssignmentService.assignBooks(members, books, 1)).willReturn(List.of(
                new WheelAssignmentService.WheelAssignment(firstMember, secondBook),
                new WheelAssignmentService.WheelAssignment(secondMember, firstBook)
        ));
        given(groupRepository.updateGroupStateToInProcessByGroupIds(
                State.IN_PROGRESS,
                State.RECRUITING,
                List.of(groupId)
        )).willReturn(1);

        int updated = groupScheduleService.updateStartedGroupsToInProgress();

        assertThat(updated).isEqualTo(1);
        assertThat(group.getGroupRoundCount()).isEqualTo(1);
        then(roundRepository).should().saveAll(anyList());
        then(wheelReassignmentService).should().savePlannedAssignments(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(members),
                org.mockito.ArgumentMatchers.eq(books)
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
