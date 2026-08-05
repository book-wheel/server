package com.bookwheel.server.schedule.service;

import com.bookwheel.server.book.entity.OwnBook;
import com.bookwheel.server.book.repository.OwnBookRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.ScheduleReconfigurationStatus;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.group.service.GroupMemberPermissionValidator;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.schedule.dto.ExcludedDateRange;
import com.bookwheel.server.schedule.dto.GroupScheduleFutureRequest;
import com.bookwheel.server.schedule.entity.Round;
import com.bookwheel.server.schedule.repository.RoundRepository;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import com.bookwheel.server.wheel.dto.WheelAssignmentPlan;
import com.bookwheel.server.wheel.entity.WheelState;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import com.bookwheel.server.wheel.service.WheelReassignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class FutureScheduleServiceTest {

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
    private GroupMemberPermissionValidator memberPermissionValidator;
    @Mock
    private WheelReassignmentService wheelReassignmentService;
    @Mock
    private ScheduleCalendarService scheduleCalendarService;

    private FutureScheduleService futureScheduleService;

    @BeforeEach
    void setUp() {
        futureScheduleService = new FutureScheduleService(
                groupRepository,
                memberRepository,
                userRepository,
                ownBookRepository,
                roundRepository,
                wheelStateRepository,
                memberPermissionValidator,
                wheelReassignmentService,
                scheduleCalendarService,
                FIXED_CLOCK
        );
    }

    @Test
    @DisplayName("읽기 순서 재확인 전에는 미래 일정을 교체할 수 없다")
    void regenerateFutureSchedule_RejectsBeforeReadOrderConfirmation() {
        String groupId = "group-1";
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(State.IN_PROGRESS)
                .scheduleReconfigurationStatus(ScheduleReconfigurationStatus.READ_ORDER_CONFIRMATION_REQUIRED)
                .build();

        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById("leader-user-pk")).willReturn(Optional.of(activeUser("리더")));

        assertThatThrownBy(() -> futureScheduleService.regenerateFutureSchedule(
                groupId,
                new GroupScheduleFutureRequest(1, 7, null, List.of(), List.of()),
                "leader-user-pk"
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GROUP_READ_ORDER_RECONFIRMATION_REQUIRED)
                );

        then(memberRepository).shouldHaveNoInteractions();
        then(roundRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("미래 라운드 최대값은 이미 읽은 책을 제외한 멤버별 남은 책 수로 제한한다")
    void regenerateFutureSchedule_RejectsRoundsBeyondUnreadBookCapacity() {
        String groupId = "group-1";
        LocalDate today = LocalDate.now(FIXED_CLOCK);
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(State.IN_PROGRESS)
                .startDate(today.minusDays(7))
                .readingPeriod(7)
                .build();
        User firstUser = activeUser("첫째");
        User secondUser = activeUser("둘째");
        User thirdUser = activeUser("셋째");
        Member firstMember = activeMember("member-1", group, firstUser);
        Member secondMember = activeMember("member-2", group, secondUser);
        Member thirdMember = activeMember("member-3", group, thirdUser);
        OwnBook firstBook = ownBook("book-1", group, firstUser);
        OwnBook secondBook = ownBook("book-2", group, secondUser);
        OwnBook thirdBook = ownBook("book-3", group, thirdUser);
        Round protectedRound = Round.builder()
                .roundId("round-1")
                .group(group)
                .roundNumber(1)
                .startDate(today.minusDays(7))
                .endDate(today.minusDays(1))
                .build();
        WheelState firstState = wheelState("state-1", protectedRound, firstMember, secondBook);
        WheelState secondState = wheelState("state-2", protectedRound, secondMember, thirdBook);
        WheelState thirdState = wheelState("state-3", protectedRound, thirdMember, firstBook);
        GroupScheduleFutureRequest request = new GroupScheduleFutureRequest(
                3,
                7,
                null,
                List.of(),
                List.of()
        );

        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById("leader-user-pk")).willReturn(Optional.of(activeUser("리더")));
        given(memberRepository.findByGroup_GroupIdAndMemberStatus(groupId, MemberStatus.ACTIVE))
                .willReturn(List.of(firstMember, secondMember, thirdMember));
        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId))
                .willReturn(List.of(protectedRound));
        given(wheelStateRepository.findByRoundIdIn(List.of("round-1")))
                .willReturn(List.of(firstState, secondState, thirdState));
        given(ownBookRepository.findByGroup_GroupIdIn(List.of(groupId)))
                .willReturn(List.of(firstBook, secondBook, thirdBook));

        assertThatThrownBy(() ->
                futureScheduleService.regenerateFutureSchedule(groupId, request, "leader-user-pk")
        )
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GROUP_FUTURE_SCHEDULE_TOTAL_EXCEEDS_ACTIVE_LIMIT)
                );

        then(wheelReassignmentService).should(never())
                .deleteReplaceableFutureAssignments(org.mockito.ArgumentMatchers.anyList());
        then(roundRepository).should(never()).deleteByRoundIdIn(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("시작된 라운드를 보존하고 미래 라운드와 일정 설정만 교체한다")
    void regenerateFutureSchedule_PreservesProtectedRoundAndReplacesFutureSchedule() {
        String groupId = "group-1";
        LocalDate today = LocalDate.now(FIXED_CLOCK);
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(State.IN_PROGRESS)
                .groupRoundCount(2)
                .startDate(today.minusDays(3))
                .readingPeriod(7)
                .scheduleReconfigurationStatus(ScheduleReconfigurationStatus.FUTURE_SCHEDULE_CONFIRMATION_REQUIRED)
                .build();
        User firstUser = activeUser("첫째");
        User secondUser = activeUser("둘째");
        User thirdUser = activeUser("셋째");
        Member firstMember = activeMember("member-1", group, firstUser);
        Member secondMember = activeMember("member-2", group, secondUser);
        Member thirdMember = activeMember("member-3", group, thirdUser);
        List<Member> activeMembers = List.of(firstMember, secondMember, thirdMember);
        OwnBook firstBook = ownBook("book-1", group, firstUser);
        OwnBook secondBook = ownBook("book-2", group, secondUser);
        OwnBook thirdBook = ownBook("book-3", group, thirdUser);
        List<OwnBook> books = List.of(firstBook, secondBook, thirdBook);
        Round protectedRound = Round.builder()
                .roundId("round-1")
                .group(group)
                .roundNumber(1)
                .startDate(today.minusDays(3))
                .endDate(today)
                .build();
        Round existingFutureRound = Round.builder()
                .roundId("round-2")
                .group(group)
                .roundNumber(2)
                .startDate(today.plusDays(1))
                .endDate(today.plusDays(7))
                .build();
        WheelState firstState = wheelState("state-1", protectedRound, firstMember, secondBook);
        WheelState secondState = wheelState("state-2", protectedRound, secondMember, thirdBook);
        WheelState thirdState = wheelState("state-3", protectedRound, thirdMember, firstBook);
        List<WheelState> protectedStates = List.of(firstState, secondState, thirdState);
        LocalDate requestedEndDate = today.plusDays(10);
        List<LocalDate> excludedDates = List.of(today.plusDays(2));
        List<ExcludedDateRange> excludedDateRanges = List.of(
                new ExcludedDateRange(today.plusDays(4), today.plusDays(5))
        );
        GroupScheduleFutureRequest request = new GroupScheduleFutureRequest(
                2,
                3,
                requestedEndDate,
                excludedDates,
                excludedDateRanges
        );
        ScheduleCalendarService.ExcludedCalendar excludedCalendar =
                org.mockito.Mockito.mock(ScheduleCalendarService.ExcludedCalendar.class);
        AtomicReference<List<Round>> savedRounds = new AtomicReference<>(List.of());

        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById("leader-user-pk")).willReturn(Optional.of(activeUser("리더")));
        given(memberRepository.findByGroup_GroupIdAndMemberStatus(groupId, MemberStatus.ACTIVE))
                .willReturn(activeMembers);
        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId))
                .willReturn(List.of(protectedRound, existingFutureRound));
        given(wheelStateRepository.findByRoundIdIn(List.of("round-1"))).willReturn(protectedStates);
        given(ownBookRepository.findByGroup_GroupIdIn(List.of(groupId))).willReturn(books);
        given(scheduleCalendarService.normalizeExcludedCalendar(excludedDates, excludedDateRanges))
                .willReturn(excludedCalendar);
        given(scheduleCalendarService.countUsableDaysUntilDeadline(
                today.plusDays(1),
                requestedEndDate,
                excludedCalendar
        )).willReturn(7L);
        given(scheduleCalendarService.calculateRoundEndDate(
                today.plusDays(1),
                3,
                excludedCalendar
        )).willReturn(today.plusDays(6));
        given(wheelReassignmentService.planFutureAssignments(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.eq(activeMembers),
                org.mockito.ArgumentMatchers.eq(books),
                org.mockito.ArgumentMatchers.eq(protectedStates)
        )).willAnswer(invocation -> {
            List<Round> newRounds = invocation.getArgument(0);
            String newRoundId = newRounds.get(0).getRoundId();
            return new WheelAssignmentPlan(List.of(
                    new WheelAssignmentPlan.Assignment(newRoundId, "member-1", "book-3"),
                    new WheelAssignmentPlan.Assignment(newRoundId, "member-2", "book-1"),
                    new WheelAssignmentPlan.Assignment(newRoundId, "member-3", "book-2")
            ));
        });
        given(roundRepository.saveAll(org.mockito.ArgumentMatchers.anyList())).willAnswer(invocation -> {
            List<Round> rounds = invocation.getArgument(0);
            savedRounds.set(rounds);
            return rounds;
        });

        futureScheduleService.regenerateFutureSchedule(groupId, request, "leader-user-pk");

        assertThat(savedRounds.get()).singleElement().satisfies(round -> {
            assertThat(round.getRoundNumber()).isEqualTo(2);
            assertThat(round.getStartDate()).isEqualTo(today.plusDays(1));
            assertThat(round.getEndDate()).isEqualTo(today.plusDays(6));
            assertThat(round.getRoundId()).isNotEqualTo(existingFutureRound.getRoundId());
        });
        assertThat(group.getGroupRoundCount()).isEqualTo(2);
        assertThat(group.getReadingPeriod()).isEqualTo(3);
        assertThat(group.getScheduleEndDate()).isEqualTo(requestedEndDate);
        assertThat(group.getScheduleExcludedDates()).isEqualTo(today.plusDays(2).toString());
        assertThat(group.getScheduleExcludedDateRanges()).isEqualTo(
                today.plusDays(4) + ":" + today.plusDays(5)
        );
        assertThat(group.getScheduleReconfigurationStatus()).isEqualTo(ScheduleReconfigurationStatus.NONE);
        then(wheelReassignmentService).should()
                .deleteReplaceableFutureAssignments(List.of(existingFutureRound));
        then(roundRepository).should().deleteByRoundIdIn(List.of("round-2"));
        then(wheelReassignmentService).should().savePlannedAssignments(
                org.mockito.ArgumentMatchers.any(WheelAssignmentPlan.class),
                org.mockito.ArgumentMatchers.eq(activeMembers),
                org.mockito.ArgumentMatchers.eq(books)
        );
    }

    @Test
    @DisplayName("미래 라운드를 모두 제거해도 종료 제한일은 마지막 보호 라운드보다 빠를 수 없다")
    void regenerateFutureSchedule_RejectsEndDateBeforeLastProtectedRoundWhenNoFutureRoundsRemain() {
        String groupId = "group-1";
        LocalDate today = LocalDate.now(FIXED_CLOCK);
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(State.IN_PROGRESS)
                .groupRoundCount(1)
                .startDate(today)
                .build();
        User firstUser = activeUser("첫째");
        User secondUser = activeUser("둘째");
        Member firstMember = activeMember("member-1", group, firstUser);
        Member secondMember = activeMember("member-2", group, secondUser);
        OwnBook firstBook = ownBook("book-1", group, firstUser);
        OwnBook secondBook = ownBook("book-2", group, secondUser);
        Round protectedRound = Round.builder()
                .roundId("round-1")
                .group(group)
                .roundNumber(1)
                .startDate(today)
                .endDate(today.plusDays(3))
                .build();
        List<WheelState> protectedStates = List.of(
                wheelState("state-1", protectedRound, firstMember, secondBook),
                wheelState("state-2", protectedRound, secondMember, firstBook)
        );
        GroupScheduleFutureRequest request = new GroupScheduleFutureRequest(
                1,
                7,
                today.plusDays(2),
                List.of(),
                List.of()
        );
        ScheduleCalendarService.ExcludedCalendar excludedCalendar =
                org.mockito.Mockito.mock(ScheduleCalendarService.ExcludedCalendar.class);

        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById("leader-user-pk")).willReturn(Optional.of(activeUser("리더")));
        given(memberRepository.findByGroup_GroupIdAndMemberStatus(groupId, MemberStatus.ACTIVE))
                .willReturn(List.of(firstMember, secondMember));
        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId))
                .willReturn(List.of(protectedRound));
        given(wheelStateRepository.findByRoundIdIn(List.of("round-1"))).willReturn(protectedStates);
        given(ownBookRepository.findByGroup_GroupIdIn(List.of(groupId)))
                .willReturn(List.of(firstBook, secondBook));
        given(scheduleCalendarService.normalizeExcludedCalendar(List.of(), List.of()))
                .willReturn(excludedCalendar);

        assertThatThrownBy(() ->
                futureScheduleService.regenerateFutureSchedule(groupId, request, "leader-user-pk")
        )
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GROUP_SCHEDULE_END_DATE_MISMATCH)
                );

        then(roundRepository).should(never()).deleteByRoundIdIn(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("미래 일정 종료 제한일은 새 미래 일정 시작일로부터 3년을 초과할 수 없다")
    void regenerateFutureSchedule_RejectsEndDateBeyondThreeYears() {
        String groupId = "group-1";
        LocalDate today = LocalDate.now(FIXED_CLOCK);
        LocalDate firstFutureStartDate = today.plusDays(1);
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(State.IN_PROGRESS)
                .groupRoundCount(1)
                .startDate(today.minusDays(1))
                .readingPeriod(7)
                .build();
        User firstUser = activeUser("첫째");
        User secondUser = activeUser("둘째");
        Member firstMember = activeMember("member-1", group, firstUser);
        Member secondMember = activeMember("member-2", group, secondUser);
        OwnBook firstBook = ownBook("book-1", group, firstUser);
        OwnBook secondBook = ownBook("book-2", group, secondUser);
        GroupScheduleFutureRequest request = new GroupScheduleFutureRequest(
                1,
                7,
                firstFutureStartDate.plusYears(3).plusDays(1),
                List.of(),
                List.of()
        );
        ScheduleCalendarService.ExcludedCalendar excludedCalendar =
                org.mockito.Mockito.mock(ScheduleCalendarService.ExcludedCalendar.class);

        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById("leader-user-pk")).willReturn(Optional.of(activeUser("리더")));
        given(memberRepository.findByGroup_GroupIdAndMemberStatus(groupId, MemberStatus.ACTIVE))
                .willReturn(List.of(firstMember, secondMember));
        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId))
                .willReturn(List.of());
        given(ownBookRepository.findByGroup_GroupIdIn(List.of(groupId)))
                .willReturn(List.of(firstBook, secondBook));
        given(scheduleCalendarService.normalizeExcludedCalendar(List.of(), List.of()))
                .willReturn(excludedCalendar);

        assertThatThrownBy(() ->
                futureScheduleService.regenerateFutureSchedule(groupId, request, "leader-user-pk")
        )
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_SCHEDULE_DURATION_EXCEEDED);

        then(wheelReassignmentService).shouldHaveNoInteractions();
        then(roundRepository).should(never()).deleteByRoundIdIn(org.mockito.ArgumentMatchers.anyList());
    }

    @ParameterizedTest(name = "{0} 상태에서는 미래 일정을 변경할 수 없다")
    @MethodSource("invalidFutureScheduleStates")
    void regenerateFutureSchedule_RejectsInvalidGroupState(State state, ErrorCode expectedErrorCode) {
        String groupId = "group-1";
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(state)
                .build();
        GroupScheduleFutureRequest request = new GroupScheduleFutureRequest(
                1,
                7,
                null,
                List.of(),
                List.of()
        );
        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById("leader-user-pk")).willReturn(Optional.of(activeUser("리더")));

        assertThatThrownBy(() ->
                futureScheduleService.regenerateFutureSchedule(groupId, request, "leader-user-pk")
        )
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode)
                );

        then(roundRepository).shouldHaveNoInteractions();
    }

    private static Stream<Arguments> invalidFutureScheduleStates() {
        return Stream.of(
                Arguments.of(State.RECRUITING, ErrorCode.GROUP_FUTURE_SCHEDULE_RECRUITING_STATE_INVALID),
                Arguments.of(State.COMPLETE, ErrorCode.GROUP_FUTURE_SCHEDULE_COMPLETE_STATE_INVALID)
        );
    }

    @Test
    @DisplayName("실행 범위 밖의 지난 날짜 틀은 보호 라운드 수에 포함하지 않는다")
    void resolveConstraints_IgnoresPastInactiveDateFrame() {
        String groupId = "group-1";
        LocalDate today = LocalDate.now(FIXED_CLOCK);
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(State.IN_PROGRESS)
                .build();
        Round executableRound = Round.builder()
                .roundId("round-1")
                .group(group)
                .roundNumber(1)
                .startDate(today.minusDays(14))
                .endDate(today.minusDays(8))
                .build();
        Round inactiveDateFrame = Round.builder()
                .roundId("round-2")
                .group(group)
                .roundNumber(2)
                .startDate(today.minusDays(7))
                .endDate(today.minusDays(1))
                .build();
        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId))
                .willReturn(List.of(executableRound, inactiveDateFrame));

        FutureScheduleService.FutureScheduleConstraints constraints =
                futureScheduleService.resolveConstraints(groupId, 1);

        assertThat(constraints.protectedRoundCount()).isEqualTo(1);
        assertThat(constraints.minTotalRoundCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("실행 범위 밖의 지난 날짜 틀을 삭제하고 내일부터 새 미래 라운드를 생성한다")
    void regenerateFutureSchedule_ReplacesPastInactiveDateFrame() {
        String groupId = "group-1";
        LocalDate today = LocalDate.now(FIXED_CLOCK);
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(State.IN_PROGRESS)
                .groupRoundCount(1)
                .startDate(today.minusDays(14))
                .readingPeriod(7)
                .build();
        User firstUser = activeUser("첫째");
        User secondUser = activeUser("둘째");
        User thirdUser = activeUser("셋째");
        Member firstMember = activeMember("member-1", group, firstUser);
        Member secondMember = activeMember("member-2", group, secondUser);
        Member thirdMember = activeMember("member-3", group, thirdUser);
        List<Member> activeMembers = List.of(firstMember, secondMember, thirdMember);
        OwnBook firstBook = ownBook("book-1", group, firstUser);
        OwnBook secondBook = ownBook("book-2", group, secondUser);
        OwnBook thirdBook = ownBook("book-3", group, thirdUser);
        List<OwnBook> books = List.of(firstBook, secondBook, thirdBook);
        Round protectedRound = Round.builder()
                .roundId("round-1")
                .group(group)
                .roundNumber(1)
                .startDate(today.minusDays(14))
                .endDate(today.minusDays(8))
                .build();
        Round inactivePastRound = Round.builder()
                .roundId("round-2")
                .group(group)
                .roundNumber(2)
                .startDate(today.minusDays(7))
                .endDate(today.minusDays(1))
                .build();
        List<WheelState> protectedStates = List.of(
                wheelState("state-1", protectedRound, firstMember, secondBook),
                wheelState("state-2", protectedRound, secondMember, thirdBook),
                wheelState("state-3", protectedRound, thirdMember, firstBook)
        );
        GroupScheduleFutureRequest request = new GroupScheduleFutureRequest(
                2,
                3,
                null,
                List.of(),
                List.of()
        );
        ScheduleCalendarService.ExcludedCalendar excludedCalendar =
                org.mockito.Mockito.mock(ScheduleCalendarService.ExcludedCalendar.class);
        AtomicReference<List<Round>> savedRounds = new AtomicReference<>(List.of());

        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById("leader-user-pk")).willReturn(Optional.of(activeUser("리더")));
        given(memberRepository.findByGroup_GroupIdAndMemberStatus(groupId, MemberStatus.ACTIVE))
                .willReturn(activeMembers);
        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId))
                .willReturn(List.of(protectedRound, inactivePastRound));
        given(wheelStateRepository.findByRoundIdIn(List.of("round-1"))).willReturn(protectedStates);
        given(wheelStateRepository.findByRoundIdIn(List.of("round-2"))).willReturn(List.of());
        given(ownBookRepository.findByGroup_GroupIdIn(List.of(groupId))).willReturn(books);
        given(scheduleCalendarService.normalizeExcludedCalendar(List.of(), List.of()))
                .willReturn(excludedCalendar);
        given(scheduleCalendarService.countUsableDaysUntilDeadline(
                today.plusDays(1),
                today.plusDays(1).plusYears(3),
                excludedCalendar
        )).willReturn(1_097L);
        given(scheduleCalendarService.calculateRoundEndDate(
                today.plusDays(1),
                3,
                excludedCalendar
        )).willReturn(today.plusDays(3));
        given(wheelReassignmentService.planFutureAssignments(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.eq(activeMembers),
                org.mockito.ArgumentMatchers.eq(books),
                org.mockito.ArgumentMatchers.eq(protectedStates)
        )).willAnswer(invocation -> {
            List<Round> newRounds = invocation.getArgument(0);
            String newRoundId = newRounds.get(0).getRoundId();
            return new WheelAssignmentPlan(List.of(
                    new WheelAssignmentPlan.Assignment(newRoundId, "member-1", "book-3"),
                    new WheelAssignmentPlan.Assignment(newRoundId, "member-2", "book-1"),
                    new WheelAssignmentPlan.Assignment(newRoundId, "member-3", "book-2")
            ));
        });
        given(roundRepository.saveAll(org.mockito.ArgumentMatchers.anyList())).willAnswer(invocation -> {
            List<Round> rounds = invocation.getArgument(0);
            savedRounds.set(rounds);
            return rounds;
        });

        futureScheduleService.regenerateFutureSchedule(groupId, request, "leader-user-pk");

        assertThat(savedRounds.get()).singleElement().satisfies(round -> {
            assertThat(round.getRoundNumber()).isEqualTo(2);
            assertThat(round.getStartDate()).isEqualTo(today.plusDays(1));
            assertThat(round.getEndDate()).isEqualTo(today.plusDays(3));
            assertThat(round.getRoundId()).isNotEqualTo(inactivePastRound.getRoundId());
        });
        then(wheelReassignmentService).should().deleteReplaceableFutureAssignments(List.of());
        then(roundRepository).should().deleteByRoundIdIn(List.of("round-2"));
        then(wheelReassignmentService).should().savePlannedAssignments(
                org.mockito.ArgumentMatchers.any(WheelAssignmentPlan.class),
                org.mockito.ArgumentMatchers.eq(activeMembers),
                org.mockito.ArgumentMatchers.eq(books)
        );
    }

    @Test
    @DisplayName("실행 범위 밖의 지난 날짜 틀에 배정이 남아 있으면 재생성을 중단한다")
    void regenerateFutureSchedule_RejectsPastInactiveDateFrameWithAssignment() {
        String groupId = "group-1";
        LocalDate today = LocalDate.now(FIXED_CLOCK);
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(State.IN_PROGRESS)
                .groupRoundCount(1)
                .startDate(today.minusDays(14))
                .readingPeriod(7)
                .build();
        User firstUser = activeUser("첫째");
        User secondUser = activeUser("둘째");
        Member firstMember = activeMember("member-1", group, firstUser);
        Member secondMember = activeMember("member-2", group, secondUser);
        OwnBook firstBook = ownBook("book-1", group, firstUser);
        OwnBook secondBook = ownBook("book-2", group, secondUser);
        Round protectedRound = Round.builder()
                .roundId("round-1")
                .group(group)
                .roundNumber(1)
                .startDate(today.minusDays(14))
                .endDate(today.minusDays(8))
                .build();
        Round inactivePastRound = Round.builder()
                .roundId("round-2")
                .group(group)
                .roundNumber(2)
                .startDate(today.minusDays(7))
                .endDate(today.minusDays(1))
                .build();
        List<WheelState> protectedStates = List.of(
                wheelState("state-1", protectedRound, firstMember, secondBook),
                wheelState("state-2", protectedRound, secondMember, firstBook)
        );
        WheelState inactiveAssignment =
                wheelState("state-3", inactivePastRound, firstMember, secondBook);
        GroupScheduleFutureRequest request = new GroupScheduleFutureRequest(
                1,
                7,
                null,
                List.of(),
                List.of()
        );
        ScheduleCalendarService.ExcludedCalendar excludedCalendar =
                org.mockito.Mockito.mock(ScheduleCalendarService.ExcludedCalendar.class);

        given(groupRepository.findByGroupIdForUpdate(groupId)).willReturn(Optional.of(group));
        given(userRepository.findById("leader-user-pk")).willReturn(Optional.of(activeUser("리더")));
        given(memberRepository.findByGroup_GroupIdAndMemberStatus(groupId, MemberStatus.ACTIVE))
                .willReturn(List.of(firstMember, secondMember));
        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(groupId))
                .willReturn(List.of(protectedRound, inactivePastRound));
        given(wheelStateRepository.findByRoundIdIn(List.of("round-1"))).willReturn(protectedStates);
        given(wheelStateRepository.findByRoundIdIn(List.of("round-2")))
                .willReturn(List.of(inactiveAssignment));
        given(ownBookRepository.findByGroup_GroupIdIn(List.of(groupId)))
                .willReturn(List.of(firstBook, secondBook));
        given(scheduleCalendarService.normalizeExcludedCalendar(List.of(), List.of()))
                .willReturn(excludedCalendar);

        assertThatThrownBy(() ->
                futureScheduleService.regenerateFutureSchedule(groupId, request, "leader-user-pk")
        )
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GROUP_FUTURE_SCHEDULE_WHEEL_STATE_INVALID)
                );

        then(wheelReassignmentService).should(never())
                .deleteReplaceableFutureAssignments(org.mockito.ArgumentMatchers.anyList());
        then(roundRepository).should(never()).deleteByRoundIdIn(org.mockito.ArgumentMatchers.anyList());
    }

    private User activeUser(String nickname) {
        return User.builder()
                .loginId(nickname + "-login")
                .nickname(nickname)
                .mail(nickname + "@example.com")
                .isActive(true)
                .build();
    }

    private Member activeMember(String memberId, Group group, User user) {
        return Member.builder()
                .memberId(memberId)
                .group(group)
                .user(user)
                .memberStatus(MemberStatus.ACTIVE)
                .build();
    }

    private OwnBook ownBook(String ownBookId, Group group, User owner) {
        return OwnBook.builder()
                .ownBookId(ownBookId)
                .group(group)
                .owner(owner)
                .build();
    }

    private WheelState wheelState(
            String wheelStateId,
            Round round,
            Member member,
            OwnBook ownBook
    ) {
        return WheelState.builder()
                .wheelStateId(wheelStateId)
                .roundId(round.getRoundId())
                .member(member)
                .ownBook(ownBook)
                .build();
    }
}
