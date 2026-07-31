package com.bookwheel.server.schedule.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.group.service.GroupMemberPermissionValidator;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.schedule.dto.GroupScheduleCreateRequest;
import com.bookwheel.server.schedule.dto.GroupScheduleBlockingReason;
import com.bookwheel.server.schedule.dto.GroupSchedulePreviewResponse;
import com.bookwheel.server.schedule.dto.GroupScheduleResponse;
import com.bookwheel.server.schedule.dto.GroupScheduleStatus;
import com.bookwheel.server.schedule.entity.Round;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

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
    private RoundRepository roundRepository;

    @Mock
    private WheelStateRepository wheelStateRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private GroupMemberPermissionValidator memberPermissionValidator;

    @Mock
    private FutureScheduleService futureScheduleService;

    @Mock
    private RecruitingScheduleAssignmentService recruitingScheduleAssignmentService;

    @Mock
    private ScheduleCalendarService scheduleCalendarService;

    private GroupScheduleService groupScheduleService;

    @BeforeEach
    void setUp() {
        groupScheduleService = new GroupScheduleService(
                groupRepository,
                memberRepository,
                userRepository,
                roundRepository,
                wheelStateRepository,
                eventPublisher,
                memberPermissionValidator,
                futureScheduleService,
                recruitingScheduleAssignmentService,
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
                List.of(),
                2
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
    @DisplayName("일정 목표 인원은 12명을 초과할 수 없다")
    void createSchedule_RejectsTargetMemberCountAboveLimit() {
        String groupId = "group-1";
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(State.RECRUITING)
                .maxMembers(100)
                .build();
        GroupScheduleCreateRequest request = new GroupScheduleCreateRequest(
                LocalDate.now(FIXED_CLOCK).plusDays(1),
                7,
                null,
                List.of(),
                List.of(),
                13
        );
        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById("leader-user-pk")).willReturn(Optional.of(activeUser()));
        given(memberRepository.countByGroup_GroupIdAndMemberStatus(
                groupId,
                com.bookwheel.server.member.enums.MemberStatus.ACTIVE
        )).willReturn(1L);

        assertThatThrownBy(() -> groupScheduleService.createSchedule(groupId, request, "leader-user-pk"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_SCHEDULE_TARGET_MEMBER_INVALID);

        then(roundRepository).should(never()).saveAll(anyList());
    }

    @Test
    @DisplayName("일정 미리보기는 목표 인원 기준 날짜만 계산하고 저장하지 않는다")
    void previewSchedule_CalculatesTargetMemberRoundsWithoutSaving() {
        String groupId = "group-1";
        LocalDate startDate = LocalDate.now(FIXED_CLOCK).plusDays(3);
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(State.RECRUITING)
                .maxMembers(12)
                .build();
        GroupScheduleCreateRequest request = new GroupScheduleCreateRequest(
                startDate,
                5,
                startDate.plusDays(60),
                List.of(),
                List.of(),
                10
        );
        ScheduleCalendarService.ExcludedCalendar excludedCalendar =
                mock(ScheduleCalendarService.ExcludedCalendar.class);
        given(groupRepository.findById(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById("leader-user-pk")).willReturn(Optional.of(activeUser()));
        given(memberRepository.countByGroup_GroupIdAndMemberStatus(
                groupId,
                com.bookwheel.server.member.enums.MemberStatus.ACTIVE
        )).willReturn(1L);
        given(scheduleCalendarService.normalizeExcludedCalendar(List.of(), List.of()))
                .willReturn(excludedCalendar);
        given(scheduleCalendarService.countUsableDaysUntilDeadline(
                startDate,
                startDate.plusDays(60),
                excludedCalendar
        )).willReturn(61L);
        given(scheduleCalendarService.calculateRoundEndDate(
                org.mockito.ArgumentMatchers.any(LocalDate.class),
                org.mockito.ArgumentMatchers.eq(5),
                org.mockito.ArgumentMatchers.eq(excludedCalendar)
        )).willAnswer(invocation -> ((LocalDate) invocation.getArgument(0)).plusDays(4));

        GroupSchedulePreviewResponse response =
                groupScheduleService.previewSchedule(groupId, request, "leader-user-pk");

        assertThat(response.targetMemberCount()).isEqualTo(10);
        assertThat(response.plannedRoundCount()).isEqualTo(9);
        assertThat(response.plannedEndDate()).isEqualTo(startDate.plusDays(44));
        assertThat(response.rounds()).hasSize(9);
        then(roundRepository).shouldHaveNoInteractions();
        then(recruitingScheduleAssignmentService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("기존 일정의 시작 당일에는 동일한 요청으로 미리보기할 수 없다")
    void previewSchedule_RejectsReplacementOnExistingStartDate() {
        String groupId = "group-1";
        LocalDate today = LocalDate.now(FIXED_CLOCK);
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(State.RECRUITING)
                .startDate(today)
                .maxMembers(10)
                .build();
        GroupScheduleCreateRequest request = new GroupScheduleCreateRequest(
                today.plusDays(3),
                7,
                null,
                List.of(),
                List.of(),
                4
        );
        given(groupRepository.findById(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById("leader-user-pk")).willReturn(Optional.of(activeUser()));
        given(roundRepository.existsByGroup_GroupId(groupId)).willReturn(true);

        assertThatThrownBy(() ->
                groupScheduleService.previewSchedule(groupId, request, "leader-user-pk")
        )
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_SCHEDULE_REPLACE_NOT_ALLOWED_ON_START_DATE);

        then(memberRepository).shouldHaveNoInteractions();
        then(scheduleCalendarService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("미리보기 종료 제한일은 일정 시작일로부터 3년을 초과할 수 없다")
    void previewSchedule_RejectsEndDateBeyondThreeYears() {
        String groupId = "group-1";
        LocalDate startDate = LocalDate.now(FIXED_CLOCK).plusDays(3);
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(State.RECRUITING)
                .maxMembers(10)
                .build();
        GroupScheduleCreateRequest request = new GroupScheduleCreateRequest(
                startDate,
                7,
                startDate.plusYears(3).plusDays(1),
                List.of(),
                List.of(),
                2
        );
        ScheduleCalendarService.ExcludedCalendar excludedCalendar =
                mock(ScheduleCalendarService.ExcludedCalendar.class);
        given(groupRepository.findById(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById("leader-user-pk")).willReturn(Optional.of(activeUser()));
        given(memberRepository.countByGroup_GroupIdAndMemberStatus(
                groupId,
                com.bookwheel.server.member.enums.MemberStatus.ACTIVE
        )).willReturn(1L);
        given(scheduleCalendarService.normalizeExcludedCalendar(List.of(), List.of()))
                .willReturn(excludedCalendar);

        assertThatThrownBy(() ->
                groupScheduleService.previewSchedule(groupId, request, "leader-user-pk")
        )
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_SCHEDULE_DURATION_EXCEEDED);

        then(scheduleCalendarService).should(never()).calculateRoundEndDate(
                org.mockito.ArgumentMatchers.any(LocalDate.class),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(ScheduleCalendarService.ExcludedCalendar.class)
        );
    }

    @Test
    @DisplayName("종료 제한일이 없어도 3년을 초과하는 미리보기 계산은 시작하지 않는다")
    void previewSchedule_RejectsCalculationBeyondThreeYearsWithoutEndDate() {
        String groupId = "group-1";
        LocalDate startDate = LocalDate.now(FIXED_CLOCK).plusDays(3);
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(State.RECRUITING)
                .maxMembers(10)
                .build();
        GroupScheduleCreateRequest request = new GroupScheduleCreateRequest(
                startDate,
                Integer.MAX_VALUE,
                null,
                List.of(),
                List.of(),
                2
        );
        ScheduleCalendarService.ExcludedCalendar excludedCalendar =
                mock(ScheduleCalendarService.ExcludedCalendar.class);
        given(groupRepository.findById(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById("leader-user-pk")).willReturn(Optional.of(activeUser()));
        given(memberRepository.countByGroup_GroupIdAndMemberStatus(
                groupId,
                com.bookwheel.server.member.enums.MemberStatus.ACTIVE
        )).willReturn(1L);
        given(scheduleCalendarService.normalizeExcludedCalendar(List.of(), List.of()))
                .willReturn(excludedCalendar);
        given(scheduleCalendarService.countUsableDaysUntilDeadline(
                startDate,
                startDate.plusYears(3),
                excludedCalendar
        )).willReturn(1_097L);

        assertThatThrownBy(() ->
                groupScheduleService.previewSchedule(groupId, request, "leader-user-pk")
        )
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_SCHEDULE_DURATION_EXCEEDED);

        then(scheduleCalendarService).should(never()).calculateRoundEndDate(
                org.mockito.ArgumentMatchers.any(LocalDate.class),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(ScheduleCalendarService.ExcludedCalendar.class)
        );
    }

    @Test
    @DisplayName("삭제된 모임의 일정 조회는 멤버 정리 여부와 관계없이 삭제 오류를 반환한다")
    void getSchedule_RejectsDeletedGroup() {
        String groupId = "deleted-group";
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("삭제된 모임")
                .groupState(State.DELETED)
                .build();
        given(groupRepository.findById(groupId)).willReturn(Optional.of(group));

        assertThatThrownBy(() -> groupScheduleService.getSchedule(groupId, "user-pk"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.GROUP_DELETED)
                );
        then(userRepository).shouldHaveNoInteractions();
        then(memberRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("ACTIVE 멤버가 아닌 사용자는 일정 조회를 할 수 없다")
    void getSchedule_RejectsNonMemberWithActiveMemberError() {
        String groupId = "group-1";
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(State.RECRUITING)
                .build();
        given(groupRepository.findById(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById("user-pk")).willReturn(Optional.of(activeUser()));
        given(memberRepository.findByGroup_GroupIdAndUser_Id(groupId, "user-pk"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> groupScheduleService.getSchedule(groupId, "user-pk"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.GROUP_ACTIVE_MEMBER_ONLY)
                );
        then(roundRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("기존 일정의 시작 당일에는 미래 날짜로도 일정을 교체할 수 없다")
    void createSchedule_RejectsReplacementOnExistingStartDate() {
        String groupId = "group-1";
        LocalDate today = LocalDate.now(FIXED_CLOCK);
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(State.RECRUITING)
                .startDate(today)
                .readingPeriod(7)
                .targetMemberCount(4)
                .maxMembers(10)
                .build();
        GroupScheduleCreateRequest request = new GroupScheduleCreateRequest(
                today.plusDays(3),
                7,
                null,
                List.of(),
                List.of(),
                4
        );
        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById("leader-user-pk")).willReturn(Optional.of(activeUser()));
        given(roundRepository.existsByGroup_GroupId(groupId)).willReturn(true);

        assertThatThrownBy(() -> groupScheduleService.createSchedule(groupId, request, "leader-user-pk"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_SCHEDULE_REPLACE_NOT_ALLOWED_ON_START_DATE);

        then(memberPermissionValidator).should().validateLeader(groupId, "leader-user-pk");
        then(roundRepository).should().existsByGroup_GroupId(groupId);
        then(roundRepository).should(never()).saveAll(anyList());
        then(roundRepository).should(never()).deleteByGroup_GroupId(groupId);
    }

    @Test
    @DisplayName("현재 멤버가 1명이어도 목표 인원 기준 라운드를 생성한다")
    void createSchedule_CreatesTargetMemberRoundsWithOneCurrentMember() {
        String groupId = "group-1";
        LocalDate startDate = LocalDate.now(FIXED_CLOCK).plusDays(3);
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(State.RECRUITING)
                .maxMembers(10)
                .build();
        GroupScheduleCreateRequest request = new GroupScheduleCreateRequest(
                startDate,
                5,
                startDate.plusDays(60),
                List.of(),
                List.of(),
                10
        );
        Member member = mock(Member.class);
        given(member.getMemberStatus()).willReturn(com.bookwheel.server.member.enums.MemberStatus.ACTIVE);
        ScheduleCalendarService.ExcludedCalendar excludedCalendar = mock(ScheduleCalendarService.ExcludedCalendar.class);
        AtomicReference<List<Round>> savedRounds = new AtomicReference<>(List.of());
        RecruitingScheduleAssignmentService.Readiness readiness =
                new RecruitingScheduleAssignmentService.Readiness(
                        false,
                        1,
                        List.of(
                                GroupScheduleBlockingReason.MINIMUM_ACTIVE_MEMBER_COUNT_NOT_REACHED,
                                GroupScheduleBlockingReason.ASSIGNMENT_INCOMPLETE
                        ),
                        List.of()
                );
        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(groupRepository.findById(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById("leader-user-pk")).willReturn(Optional.of(activeUser()));
        given(memberRepository.findByGroup_GroupIdAndUser_Id(groupId, "leader-user-pk"))
                .willReturn(Optional.of(member));
        given(scheduleCalendarService.normalizeExcludedCalendar(List.of(), List.of())).willReturn(excludedCalendar);
        given(scheduleCalendarService.countUsableDaysUntilDeadline(
                startDate,
                startDate.plusDays(60),
                excludedCalendar
        )).willReturn(61L);
        given(scheduleCalendarService.calculateRoundEndDate(
                org.mockito.ArgumentMatchers.any(LocalDate.class),
                org.mockito.ArgumentMatchers.eq(5),
                org.mockito.ArgumentMatchers.eq(excludedCalendar)
        )).willAnswer(invocation -> ((LocalDate) invocation.getArgument(0)).plusDays(4));
        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId))
                .willAnswer(invocation -> savedRounds.get());
        given(roundRepository.saveAll(anyList())).willAnswer(invocation -> {
            List<Round> rounds = invocation.getArgument(0);
            savedRounds.set(rounds);
            return rounds;
        });
        given(recruitingScheduleAssignmentService.refreshPlannedAssignments(group)).willReturn(readiness);
        given(recruitingScheduleAssignmentService.evaluate(group)).willReturn(readiness);

        GroupScheduleResponse response =
                groupScheduleService.createSchedule(groupId, request, "leader-user-pk");

        assertThat(response.startDate()).isEqualTo(startDate);
        assertThat(response.readingPeriod()).isEqualTo(5);
        assertThat(response.endDate()).isEqualTo(startDate.plusDays(60));
        assertThat(response.excludedDates()).isEmpty();
        assertThat(response.excludedDateRanges()).isEmpty();
        assertThat(response.scheduleStatus()).isEqualTo(GroupScheduleStatus.CONFIGURED);
        assertThat(response.targetMemberCount()).isEqualTo(10);
        assertThat(response.currentMemberCount()).isEqualTo(1);
        assertThat(response.canStart()).isFalse();
        assertThat(response.plannedRoundCount()).isEqualTo(9);
        assertThat(response.executableRoundCount()).isZero();
        assertThat(response.plannedEndDate()).isEqualTo(startDate.plusDays(44));
        assertThat(response.executableEndDate()).isNull();
        assertThat(response.rounds()).hasSize(9);
        assertThat(response.rounds()).allMatch(round -> !round.executable());
        assertThat(group.getStartDate()).isEqualTo(startDate);
        assertThat(group.getReadingPeriod()).isEqualTo(5);
        assertThat(group.getGroupRoundCount()).isEqualTo(9);
        assertThat(group.getTargetMemberCount()).isEqualTo(10);
        assertThat(group.getScheduleEndDate()).isEqualTo(startDate.plusDays(60));
        then(roundRepository).should().saveAll(anyList());
    }

    @Test
    @DisplayName("현재 인원 기준 실행 대상의 전체 PLANNED 배정이 준비된 일정만 READY를 반환한다")
    void createSchedule_ReturnsReadyOnlyWhenAssignmentsAreComplete() {
        String groupId = "group-1";
        LocalDate startDate = LocalDate.now(FIXED_CLOCK).plusDays(3);
        LocalDate endDate = startDate.plusDays(6);
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(State.RECRUITING)
                .maxMembers(2)
                .build();
        GroupScheduleCreateRequest request = new GroupScheduleCreateRequest(
                startDate,
                7,
                null,
                List.of(),
                List.of(),
                2
        );
        Member member = mock(Member.class);
        given(member.getMemberStatus()).willReturn(com.bookwheel.server.member.enums.MemberStatus.ACTIVE);
        ScheduleCalendarService.ExcludedCalendar excludedCalendar = mock(ScheduleCalendarService.ExcludedCalendar.class);
        AtomicReference<List<Round>> savedRounds = new AtomicReference<>(List.of());
        RecruitingScheduleAssignmentService.Readiness readiness =
                new RecruitingScheduleAssignmentService.Readiness(true, 2, List.of(), List.of());
        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(groupRepository.findById(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById("leader-user-pk")).willReturn(Optional.of(activeUser()));
        given(memberRepository.findByGroup_GroupIdAndUser_Id(groupId, "leader-user-pk"))
                .willReturn(Optional.of(member));
        given(scheduleCalendarService.normalizeExcludedCalendar(List.of(), List.of())).willReturn(excludedCalendar);
        given(scheduleCalendarService.countUsableDaysUntilDeadline(
                startDate,
                startDate.plusYears(3),
                excludedCalendar
        )).willReturn(1_097L);
        given(scheduleCalendarService.calculateRoundEndDate(startDate, 7, excludedCalendar)).willReturn(endDate);
        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId))
                .willAnswer(invocation -> savedRounds.get());
        given(roundRepository.saveAll(anyList())).willAnswer(invocation -> {
            List<Round> rounds = invocation.getArgument(0);
            savedRounds.set(rounds);
            return rounds;
        });
        given(recruitingScheduleAssignmentService.refreshPlannedAssignments(group)).willReturn(readiness);
        given(recruitingScheduleAssignmentService.evaluate(group)).willReturn(readiness);

        GroupScheduleResponse response =
                groupScheduleService.createSchedule(groupId, request, "leader-user-pk");

        assertThat(response.scheduleStatus()).isEqualTo(GroupScheduleStatus.READY);
        assertThat(response.canStart()).isTrue();
        assertThat(response.plannedRoundCount()).isEqualTo(1);
        assertThat(response.executableRoundCount()).isEqualTo(1);
        assertThat(response.plannedEndDate()).isEqualTo(endDate);
        assertThat(response.executableEndDate()).isEqualTo(endDate);
        assertThat(response.rounds()).singleElement().satisfies(round -> {
            assertThat(round.roundNumber()).isEqualTo(1);
            assertThat(round.startDate()).isEqualTo(startDate);
            assertThat(round.endDate()).isEqualTo(endDate);
            assertThat(round.executable()).isTrue();
            assertThat(round.wheelStateId()).isNull();
        });
        then(roundRepository).should().saveAll(anyList());
    }

    @Test
    @DisplayName("예정 시작일을 놓친 모집 중 일정은 재설정 필요 상태와 설정값을 반환한다")
    void getSchedule_ReturnsRescheduleRequiredWithSavedSettings() {
        String groupId = "group-1";
        LocalDate today = LocalDate.now(FIXED_CLOCK);
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(State.RECRUITING)
                .startDate(today.minusDays(1))
                .readingPeriod(5)
                .targetMemberCount(2)
                .scheduleEndDate(today.plusDays(30))
                .scheduleExcludedDates(today.plusDays(2).toString())
                .scheduleExcludedDateRanges(today.plusDays(4) + ":" + today.plusDays(6))
                .build();
        Member member = mock(Member.class);
        given(member.getMemberStatus()).willReturn(com.bookwheel.server.member.enums.MemberStatus.ACTIVE);
        given(groupRepository.findById(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById("leader-user-pk")).willReturn(Optional.of(activeUser()));
        given(memberRepository.findByGroup_GroupIdAndUser_Id(groupId, "leader-user-pk"))
                .willReturn(Optional.of(member));
        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId)).willReturn(List.of());
        given(recruitingScheduleAssignmentService.evaluate(group)).willReturn(
                new RecruitingScheduleAssignmentService.Readiness(
                        false,
                        1,
                        List.of(GroupScheduleBlockingReason.MINIMUM_ACTIVE_MEMBER_COUNT_NOT_REACHED),
                        List.of()
                )
        );

        GroupScheduleResponse response = groupScheduleService.getSchedule(groupId, "leader-user-pk");

        assertThat(response.startDate()).isEqualTo(today.minusDays(1));
        assertThat(response.readingPeriod()).isEqualTo(5);
        assertThat(response.endDate()).isEqualTo(today.plusDays(30));
        assertThat(response.excludedDates()).containsExactly(today.plusDays(2));
        assertThat(response.excludedDateRanges()).singleElement().satisfies(range -> {
            assertThat(range.startDate()).isEqualTo(today.plusDays(4));
            assertThat(range.endDate()).isEqualTo(today.plusDays(6));
        });
        assertThat(response.scheduleStatus()).isEqualTo(GroupScheduleStatus.RESCHEDULE_REQUIRED);
        assertThat(response.rounds()).isEmpty();
    }

    @Test
    @DisplayName("시작일을 놓친 일정은 배정이 남아 있어도 실행 가능한 라운드로 표시하지 않는다")
    void getSchedule_DoesNotExposeExpiredReadyRoundsAsExecutable() {
        String groupId = "group-1";
        LocalDate today = LocalDate.now(FIXED_CLOCK);
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(State.RECRUITING)
                .startDate(today.minusDays(1))
                .readingPeriod(7)
                .targetMemberCount(2)
                .groupRoundCount(1)
                .build();
        Member member = mock(Member.class);
        Round round = Round.builder()
                .roundId("round-1")
                .group(group)
                .roundNumber(1)
                .startDate(today.minusDays(1))
                .endDate(today.plusDays(5))
                .build();
        given(member.getMemberStatus()).willReturn(com.bookwheel.server.member.enums.MemberStatus.ACTIVE);
        given(member.getMemberId()).willReturn("member-1");
        given(groupRepository.findById(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById("leader-user-pk")).willReturn(Optional.of(activeUser()));
        given(memberRepository.findByGroup_GroupIdAndUser_Id(groupId, "leader-user-pk"))
                .willReturn(Optional.of(member));
        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId)).willReturn(List.of(round));
        given(wheelStateRepository.findAllByMemberIdAndRoundIdInWithBook(
                "member-1",
                List.of(round.getRoundId())
        )).willReturn(List.of());
        given(wheelStateRepository.findAllByRoundIdInWithMemberAndBook(List.of(round.getRoundId())))
                .willReturn(List.of());
        given(recruitingScheduleAssignmentService.evaluate(group)).willReturn(
                new RecruitingScheduleAssignmentService.Readiness(true, 2, List.of(), List.of())
        );

        GroupScheduleResponse response = groupScheduleService.getSchedule(groupId, "leader-user-pk");

        assertThat(response.scheduleStatus()).isEqualTo(GroupScheduleStatus.RESCHEDULE_REQUIRED);
        assertThat(response.canStart()).isFalse();
        assertThat(response.executableRoundCount()).isZero();
        assertThat(response.executableEndDate()).isNull();
        assertThat(response.rounds()).singleElement().satisfies(savedRound ->
                assertThat(savedRound.executable()).isFalse()
        );
    }

    @Test
    @DisplayName("목표 인원 날짜 틀과 현재 인원 실행 범위를 분리해 반환한다")
    void getSchedule_SeparatesPlannedAndExecutableRounds() {
        String groupId = "group-1";
        LocalDate startDate = LocalDate.now(FIXED_CLOCK).plusDays(3);
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(State.RECRUITING)
                .startDate(startDate)
                .readingPeriod(7)
                .targetMemberCount(4)
                .build();
        Member member = mock(Member.class);
        given(member.getMemberStatus()).willReturn(com.bookwheel.server.member.enums.MemberStatus.ACTIVE);
        List<Round> rounds = List.of(
                Round.builder()
                        .roundId("round-1")
                        .group(group)
                        .roundNumber(1)
                        .startDate(startDate)
                        .endDate(startDate.plusDays(6))
                        .build(),
                Round.builder()
                        .roundId("round-2")
                        .group(group)
                        .roundNumber(2)
                        .startDate(startDate.plusDays(7))
                        .endDate(startDate.plusDays(13))
                        .build(),
                Round.builder()
                        .roundId("round-3")
                        .group(group)
                        .roundNumber(3)
                        .startDate(startDate.plusDays(14))
                        .endDate(startDate.plusDays(20))
                        .build()
        );
        given(groupRepository.findById(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById("leader-user-pk")).willReturn(Optional.of(activeUser()));
        given(memberRepository.findByGroup_GroupIdAndUser_Id(groupId, "leader-user-pk"))
                .willReturn(Optional.of(member));
        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId)).willReturn(rounds);
        given(recruitingScheduleAssignmentService.evaluate(group)).willReturn(
                new RecruitingScheduleAssignmentService.Readiness(true, 2, List.of(), List.of())
        );

        GroupScheduleResponse response = groupScheduleService.getSchedule(groupId, "leader-user-pk");

        assertThat(response.plannedRoundCount()).isEqualTo(3);
        assertThat(response.executableRoundCount()).isEqualTo(1);
        assertThat(response.plannedEndDate()).isEqualTo(startDate.plusDays(20));
        assertThat(response.executableEndDate()).isEqualTo(startDate.plusDays(6));
        assertThat(response.rounds())
                .extracting(round -> round.executable())
                .containsExactly(true, false, false);
    }

    @Test
    @DisplayName("목표 인원 전이라도 시작일에 준비된 현재 인원 기준 라운드만 실행 수로 확정한다")
    void updateStartedGroups_StartsOnlyPreparedExistingSchedule() {
        String groupId = "group-1";
        LocalDate today = LocalDate.now(FIXED_CLOCK);
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(State.RECRUITING)
                .startDate(today)
                .readingPeriod(7)
                .targetMemberCount(4)
                .build();
        Member firstMember = mock(Member.class);
        Member secondMember = mock(Member.class);
        List<Member> members = List.of(firstMember, secondMember);
        Round firstRound = Round.builder()
                .roundId("round-1")
                .group(group)
                .roundNumber(1)
                .startDate(today)
                .endDate(today.plusDays(6))
                .build();
        Round secondRound = Round.builder()
                .roundId("round-2")
                .group(group)
                .roundNumber(2)
                .startDate(today.plusDays(7))
                .endDate(today.plusDays(13))
                .build();
        Round thirdRound = Round.builder()
                .roundId("round-3")
                .group(group)
                .roundNumber(3)
                .startDate(today.plusDays(14))
                .endDate(today.plusDays(20))
                .build();
        given(groupRepository.findByGroupStateAndStartDate(State.RECRUITING, today))
                .willReturn(List.of(group));
        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(memberRepository.findByGroupIdAndMemberStatusForUpdate(
                groupId,
                com.bookwheel.server.member.enums.MemberStatus.ACTIVE
        )).willReturn(members);
        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId))
                .willReturn(List.of(firstRound, secondRound, thirdRound));
        given(recruitingScheduleAssignmentService.isReady(group)).willReturn(true);
        given(groupRepository.updateGroupStateToInProcessByGroupIds(
                State.IN_PROGRESS,
                State.RECRUITING,
                List.of(groupId)
        )).willReturn(1);

        int updated = groupScheduleService.updateStartedGroupsToInProgress();

        assertThat(updated).isEqualTo(1);
        assertThat(group.getGroupRoundCount()).isEqualTo(1);
        then(roundRepository).should(never()).saveAll(anyList());
        then(roundRepository).should(never()).deleteByGroup_GroupId(groupId);
    }

    @Test
    @DisplayName("목표 인원이 없는 기존 일정도 라운드 수로 목표를 복원해 시작한다")
    void updateStartedGroups_RecoversLegacyTargetMemberCount() {
        String groupId = "legacy-group";
        LocalDate today = LocalDate.now(FIXED_CLOCK);
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("기존 모임")
                .groupState(State.RECRUITING)
                .startDate(today)
                .readingPeriod(7)
                .build();
        List<Member> members = List.of(mock(Member.class), mock(Member.class));
        Round round = Round.builder()
                .roundId("round-1")
                .group(group)
                .roundNumber(1)
                .startDate(today)
                .endDate(today.plusDays(6))
                .build();
        given(groupRepository.findByGroupStateAndStartDate(State.RECRUITING, today))
                .willReturn(List.of(group));
        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(memberRepository.findByGroupIdAndMemberStatusForUpdate(
                groupId,
                com.bookwheel.server.member.enums.MemberStatus.ACTIVE
        )).willReturn(members);
        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId))
                .willReturn(List.of(round));
        given(recruitingScheduleAssignmentService.isReady(group)).willReturn(true);
        given(groupRepository.updateGroupStateToInProcessByGroupIds(
                State.IN_PROGRESS,
                State.RECRUITING,
                List.of(groupId)
        )).willReturn(1);

        int updated = groupScheduleService.updateStartedGroupsToInProgress();

        assertThat(updated).isEqualTo(1);
        assertThat(group.getTargetMemberCount()).isEqualTo(2);
        assertThat(group.getGroupRoundCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("예정 시작일이 지난 모임은 조건이 충족돼도 자동 시작하지 않는다")
    void updateStartedGroups_DoesNotStartAfterScheduledDate() {
        String groupId = "group-1";
        LocalDate today = LocalDate.now(FIXED_CLOCK);
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(State.RECRUITING)
                .startDate(today.minusDays(1))
                .readingPeriod(7)
                .build();
        // 조회 후 시작일이 변경되는 경쟁 상황까지 방어하는지 확인한다.
        given(groupRepository.findByGroupStateAndStartDate(State.RECRUITING, today))
                .willReturn(List.of(group));
        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));

        int updated = groupScheduleService.updateStartedGroupsToInProgress();

        assertThat(updated).isZero();
        then(memberRepository).shouldHaveNoInteractions();
        then(groupRepository).should(never()).updateGroupStateToInProcessByGroupIds(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                anyList()
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
