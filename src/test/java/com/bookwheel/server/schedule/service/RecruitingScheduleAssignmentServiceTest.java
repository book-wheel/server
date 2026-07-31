package com.bookwheel.server.schedule.service;

import com.bookwheel.server.book.entity.OwnBook;
import com.bookwheel.server.book.repository.OwnBookRepository;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.schedule.dto.GroupScheduleBlockingReason;
import com.bookwheel.server.schedule.entity.Round;
import com.bookwheel.server.schedule.repository.RoundRepository;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.wheel.entity.WheelState;
import com.bookwheel.server.wheel.enums.WheelStatus;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import com.bookwheel.server.wheel.service.WheelAssignmentService;
import com.bookwheel.server.wheel.service.WheelReassignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RecruitingScheduleAssignmentServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private OwnBookRepository ownBookRepository;

    @Mock
    private RoundRepository roundRepository;

    @Mock
    private WheelStateRepository wheelStateRepository;

    @Mock
    private WheelAssignmentService wheelAssignmentService;

    @Mock
    private WheelReassignmentService wheelReassignmentService;

    private RecruitingScheduleAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new RecruitingScheduleAssignmentService(
                memberRepository,
                ownBookRepository,
                roundRepository,
                wheelStateRepository,
                wheelAssignmentService,
                wheelReassignmentService
        );
    }

    @Test
    @DisplayName("현재 인원과 도서가 준비되면 실행 대상 라운드에 PLANNED 배정을 생성한다")
    void refreshPlannedAssignments_CreatesCompletePlan() {
        Group group = group(2);
        Round round = round(group, 1);
        Member firstMember = member("member-1", "user-pk-1");
        Member secondMember = member("member-2", "user-pk-2");
        OwnBook firstBook = ownBook();
        OwnBook secondBook = ownBook();
        given(firstBook.getOwnBookId()).willReturn("book-1");
        given(secondBook.getOwnBookId()).willReturn("book-2");
        List<Member> members = List.of(firstMember, secondMember);
        List<OwnBook> books = List.of(firstBook, secondBook);
        WheelState firstState = plannedState(round, firstMember);
        WheelState secondState = plannedState(round, secondMember);

        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(group.getGroupId()))
                .willReturn(List.of(round));
        given(memberRepository.findByGroup_GroupIdAndMemberStatusOrderByReadOrderAsc(
                group.getGroupId(),
                MemberStatus.ACTIVE
        )).willReturn(members);
        given(ownBookRepository.findByGroup_GroupIdIn(List.of(group.getGroupId()))).willReturn(books);
        given(wheelStateRepository.findByRoundIdInForUpdate(List.of(round.getRoundId()))).willReturn(List.of());
        given(wheelAssignmentService.findMembersWithoutBook(members, books)).willReturn(List.of());
        given(wheelAssignmentService.assignBooks(members, books, 1)).willReturn(List.of(
                new WheelAssignmentService.WheelAssignment(firstMember, secondBook),
                new WheelAssignmentService.WheelAssignment(secondMember, firstBook)
        ));
        given(wheelStateRepository.findByRoundIdIn(List.of(round.getRoundId())))
                .willReturn(List.of(firstState, secondState));

        RecruitingScheduleAssignmentService.Readiness readiness = service.refreshPlannedAssignments(group);

        assertThat(readiness.ready()).isTrue();
        assertThat(readiness.blockingReasons()).isEmpty();
        then(wheelReassignmentService).should().savePlannedAssignments(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(members),
                org.mockito.ArgumentMatchers.eq(books)
        );
        then(roundRepository).should(never()).deleteByGroup_GroupId(group.getGroupId());
    }

    @Test
    @DisplayName("목표 인원을 다 채우지 않아도 현재 인원 기준 실행 라운드가 준비되면 READY다")
    void refreshPlannedAssignments_CreatesOnlyCurrentlyExecutableRounds() {
        Group group = group(4);
        Round firstRound = round(group, 1);
        Round secondRound = round(group, 2);
        Round thirdRound = round(group, 3);
        List<Round> rounds = List.of(firstRound, secondRound, thirdRound);
        Member firstMember = member("member-1", "user-pk-1");
        Member secondMember = member("member-2", "user-pk-2");
        List<Member> members = List.of(firstMember, secondMember);
        OwnBook firstBook = ownBook();
        OwnBook secondBook = ownBook();
        given(firstBook.getOwnBookId()).willReturn("book-1");
        given(secondBook.getOwnBookId()).willReturn("book-2");
        List<OwnBook> books = List.of(firstBook, secondBook);
        WheelState firstState = plannedState(firstRound, firstMember);
        WheelState secondState = plannedState(firstRound, secondMember);
        List<String> roundIds = rounds.stream().map(Round::getRoundId).toList();

        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(group.getGroupId()))
                .willReturn(rounds);
        given(memberRepository.findByGroup_GroupIdAndMemberStatusOrderByReadOrderAsc(
                group.getGroupId(),
                MemberStatus.ACTIVE
        )).willReturn(members);
        given(ownBookRepository.findByGroup_GroupIdIn(List.of(group.getGroupId()))).willReturn(books);
        given(wheelStateRepository.findByRoundIdInForUpdate(roundIds)).willReturn(List.of());
        given(wheelAssignmentService.findMembersWithoutBook(members, books)).willReturn(List.of());
        given(wheelAssignmentService.assignBooks(members, books, 1)).willReturn(List.of(
                new WheelAssignmentService.WheelAssignment(firstMember, secondBook),
                new WheelAssignmentService.WheelAssignment(secondMember, firstBook)
        ));
        given(wheelStateRepository.findByRoundIdIn(roundIds))
                .willReturn(List.of(firstState, secondState));

        RecruitingScheduleAssignmentService.Readiness readiness = service.refreshPlannedAssignments(group);

        assertThat(readiness.ready()).isTrue();
        assertThat(readiness.currentMemberCount()).isEqualTo(2);
        assertThat(readiness.blockingReasons()).isEmpty();
        then(wheelAssignmentService).should().assignBooks(members, books, 1);
        then(wheelAssignmentService).should(never()).assignBooks(members, books, 2);
        then(wheelAssignmentService).should(never()).assignBooks(members, books, 3);
    }

    @Test
    @DisplayName("기존 일정의 목표 인원이 없으면 저장된 라운드 수로 복원해 READY를 판단한다")
    void evaluate_InfersLegacyTargetMemberCountFromRounds() {
        Group group = Group.builder()
                .groupId("group-1")
                .groupName("기존 모임")
                .groupState(State.RECRUITING)
                .build();
        Round firstRound = round(group, 1);
        Round secondRound = round(group, 2);
        List<Round> rounds = List.of(firstRound, secondRound);
        Member firstMember = member("member-1", "user-pk-1");
        Member secondMember = member("member-2", "user-pk-2");
        Member thirdMember = member("member-3", "user-pk-3");
        List<Member> members = List.of(firstMember, secondMember, thirdMember);
        List<OwnBook> books = List.of(ownBook(), ownBook(), ownBook());
        List<WheelState> states = List.of(
                plannedState(firstRound, firstMember),
                plannedState(firstRound, secondMember),
                plannedState(firstRound, thirdMember),
                plannedState(secondRound, firstMember),
                plannedState(secondRound, secondMember),
                plannedState(secondRound, thirdMember)
        );

        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(group.getGroupId()))
                .willReturn(rounds);
        given(memberRepository.findByGroup_GroupIdAndMemberStatusOrderByReadOrderAsc(
                group.getGroupId(),
                MemberStatus.ACTIVE
        )).willReturn(members);
        given(ownBookRepository.findByGroup_GroupIdIn(List.of(group.getGroupId()))).willReturn(books);
        given(wheelStateRepository.findByRoundIdIn(rounds.stream().map(Round::getRoundId).toList()))
                .willReturn(states);
        given(wheelAssignmentService.findMembersWithoutBook(members, books)).willReturn(List.of());

        RecruitingScheduleAssignmentService.Readiness readiness = service.evaluate(group);

        assertThat(readiness.ready()).isTrue();
        assertThat(readiness.blockingReasons()).isEmpty();
    }

    @Test
    @DisplayName("책 미등록 상태는 멤버 정보와 단일 차단 사유로 반환한다")
    void evaluate_ReturnsMissingBookMemberWithoutDuplicateAssignmentReason() {
        Group group = group(2);
        Round round = round(group, 1);
        Member registeredMember = member("member-1", "registered-user");
        Member missingMember = member("member-2", "missing-user");
        List<Member> members = List.of(registeredMember, missingMember);
        List<OwnBook> books = List.of(ownBook());

        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(group.getGroupId()))
                .willReturn(List.of(round));
        given(memberRepository.findByGroup_GroupIdAndMemberStatusOrderByReadOrderAsc(
                group.getGroupId(),
                MemberStatus.ACTIVE
        )).willReturn(members);
        given(ownBookRepository.findByGroup_GroupIdIn(List.of(group.getGroupId()))).willReturn(books);
        given(wheelStateRepository.findByRoundIdIn(List.of(round.getRoundId()))).willReturn(List.of());
        given(wheelAssignmentService.findMembersWithoutBook(members, books))
                .willReturn(List.of(missingMember));

        RecruitingScheduleAssignmentService.Readiness readiness = service.evaluate(group);

        assertThat(readiness.blockingReasons())
                .containsExactly(GroupScheduleBlockingReason.MEMBER_BOOK_NOT_REGISTERED);
        assertThat(readiness.missingBookMembers()).singleElement().satisfies(missing -> {
            assertThat(missing.userPK()).isEqualTo(missingMember.getUser().getId());
            assertThat(missing.nickname()).isEqualTo("missing-user");
        });
    }

    @Test
    @DisplayName("현재 인원이 1명이면 라운드는 유지하고 기존 PLANNED 배정만 제거한다")
    void refreshPlannedAssignments_ClearsPlanWithoutDeletingRounds() {
        Group group = group(3);
        Round firstRound = round(group, 1);
        Round secondRound = round(group, 2);
        Member member = member("member-1", "user-pk-1");
        OwnBook book = ownBook();
        WheelState existingState = mock(WheelState.class);
        given(existingState.getWheelState()).willReturn(WheelStatus.PLANNED);
        List<Round> rounds = List.of(firstRound, secondRound);

        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(group.getGroupId()))
                .willReturn(rounds);
        given(memberRepository.findByGroup_GroupIdAndMemberStatusOrderByReadOrderAsc(
                group.getGroupId(),
                MemberStatus.ACTIVE
        )).willReturn(List.of(member));
        given(ownBookRepository.findByGroup_GroupIdIn(List.of(group.getGroupId()))).willReturn(List.of(book));
        given(wheelStateRepository.findByRoundIdInForUpdate(List.of(
                firstRound.getRoundId(),
                secondRound.getRoundId()
        ))).willReturn(List.of(existingState));
        given(wheelAssignmentService.findMembersWithoutBook(List.of(member), List.of(book)))
                .willReturn(List.of());

        RecruitingScheduleAssignmentService.Readiness readiness = service.refreshPlannedAssignments(group);

        assertThat(readiness.ready()).isFalse();
        assertThat(readiness.blockingReasons())
                .containsExactly(GroupScheduleBlockingReason.MINIMUM_ACTIVE_MEMBER_COUNT_NOT_REACHED);
        then(wheelStateRepository).should().deleteByRoundIdInAndWheelState(
                List.of(firstRound.getRoundId(), secondRound.getRoundId()),
                WheelStatus.PLANNED
        );
        then(wheelReassignmentService).shouldHaveNoInteractions();
        then(roundRepository).should(never()).deleteByGroup_GroupId(group.getGroupId());
    }

    private Group group(int targetMemberCount) {
        return Group.builder()
                .groupId("group-1")
                .groupName("모임")
                .groupState(State.RECRUITING)
                .targetMemberCount(targetMemberCount)
                .build();
    }

    private Round round(Group group, int roundNumber) {
        LocalDate startDate = LocalDate.of(2026, 8, 1).plusDays(roundNumber - 1L);
        return Round.builder()
                .roundId("round-" + roundNumber)
                .group(group)
                .roundNumber(roundNumber)
                .startDate(startDate)
                .endDate(startDate)
                .build();
    }

    private Member member(String memberId, String userPK) {
        User user = User.builder()
                .loginId(userPK)
                .password("password")
                .nickname(userPK)
                .mail(userPK + "@example.com")
                .isActive(true)
                .build();
        return Member.builder()
                .memberId(memberId)
                .user(user)
                .memberStatus(MemberStatus.ACTIVE)
                .build();
    }

    private OwnBook ownBook() {
        return mock(OwnBook.class);
    }

    private WheelState plannedState(Round round, Member member) {
        WheelState state = mock(WheelState.class);
        given(state.getWheelState()).willReturn(WheelStatus.PLANNED);
        given(state.getRoundId()).willReturn(round.getRoundId());
        given(state.getMember()).willReturn(member);
        return state;
    }
}
