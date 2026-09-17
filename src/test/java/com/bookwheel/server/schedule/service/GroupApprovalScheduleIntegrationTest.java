package com.bookwheel.server.schedule.service;

import com.bookwheel.server.book.entity.OwnBook;
import com.bookwheel.server.book.repository.OwnBookRepository;
import com.bookwheel.server.chat.repository.ChatRoomRepository;
import com.bookwheel.server.group.dto.setting.MemberRequestStatus;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.group.service.GroupMemberPermissionValidator;
import com.bookwheel.server.group.service.GroupService;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberRole;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.schedule.entity.Round;
import com.bookwheel.server.schedule.repository.RoundRepository;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import com.bookwheel.server.wheel.entity.WheelState;
import com.bookwheel.server.wheel.enums.WheelStatus;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import com.bookwheel.server.wheel.service.WheelAssignmentService;
import com.bookwheel.server.wheel.service.WheelReassignmentService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class GroupApprovalScheduleIntegrationTest {

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
    private OwnBookRepository ownBookRepository;
    @Mock
    private WheelStateRepository wheelStateRepository;
    @Mock
    private WheelAssignmentService wheelAssignmentService;
    @Mock
    private WheelReassignmentService wheelReassignmentService;

    @Test
    @DisplayName("기존 목표 인원을 넘는 가입 승인도 라운드 동기화 후 실제 PLANNED 재배정을 수행한다")
    void approveMember_SynchronizesLegacyScheduleBeforeReassignment() {
        Group group = Group.builder()
                .groupId("group-1")
                .groupName("기존 일정 모임")
                .groupState(State.RECRUITING)
                .startDate(LocalDate.of(2026, 8, 1))
                .readingPeriod(1)
                .maxMembers(4)
                .targetMemberCount(2)
                .groupRoundCount(1)
                .currentMembers(2)
                .build();
        Member leader = activeMember("member-1", group, "leader-user-pk", 1);
        Member member = activeMember("member-2", group, "member-user-pk", 2);
        Member pendingMember = Member.builder()
                .memberId("member-3")
                .group(group)
                .user(user("new-user-pk"))
                .memberRole(MemberRole.MEMBER)
                .memberStatus(MemberStatus.PENDING)
                .readOrder(3)
                .build();
        OwnBook firstBook = ownBook("book-1");
        OwnBook secondBook = ownBook("book-2");
        OwnBook thirdBook = ownBook("book-3");
        List<Member> activeMembers = List.of(leader, member, pendingMember);
        List<OwnBook> books = List.of(firstBook, secondBook, thirdBook);

        Round firstRound = round(group, 1, LocalDate.of(2026, 8, 1));
        List<Round> storedRounds = new ArrayList<>(List.of(firstRound));
        given(roundRepository.findByGroup_GroupIdOrderByRoundNumberAsc(group.getGroupId()))
                .willAnswer(invocation -> List.copyOf(storedRounds));
        given(roundRepository.saveAll(anyList())).willAnswer(invocation -> {
            List<Round> addedRounds = invocation.getArgument(0);
            storedRounds.addAll(addedRounds);
            return addedRounds;
        });
        given(groupRepository.findByGroupIdForUpdate(group.getGroupId())).willReturn(Optional.of(group));
        given(memberRepository.findByMemberIdAndGroup_GroupId(
                pendingMember.getMemberId(),
                group.getGroupId()
        )).willReturn(Optional.of(pendingMember));
        given(memberRepository.findByGroup_GroupIdAndMemberStatusOrderByReadOrderAsc(
                group.getGroupId(),
                MemberStatus.ACTIVE
        )).willReturn(activeMembers);
        given(ownBookRepository.findByGroup_GroupIdIn(List.of(group.getGroupId()))).willReturn(books);
        given(wheelStateRepository.findByRoundIdInForUpdate(anyList())).willReturn(List.of(
                plannedState(firstRound, leader, firstBook),
                plannedState(firstRound, member, secondBook)
        ));
        given(wheelStateRepository.findByRoundIdIn(anyList())).willAnswer(invocation -> List.of(
                plannedState(storedRounds.get(0), leader, firstBook),
                plannedState(storedRounds.get(0), member, secondBook),
                plannedState(storedRounds.get(0), pendingMember, thirdBook),
                plannedState(storedRounds.get(1), leader, secondBook),
                plannedState(storedRounds.get(1), member, thirdBook),
                plannedState(storedRounds.get(1), pendingMember, firstBook)
        ));
        given(wheelAssignmentService.findMembersWithoutBook(activeMembers, books)).willReturn(List.of());
        given(wheelAssignmentService.assignBooks(activeMembers, books, 1)).willReturn(List.of(
                new WheelAssignmentService.WheelAssignment(leader, secondBook),
                new WheelAssignmentService.WheelAssignment(member, thirdBook),
                new WheelAssignmentService.WheelAssignment(pendingMember, firstBook)
        ));
        given(wheelAssignmentService.assignBooks(activeMembers, books, 2)).willReturn(List.of(
                new WheelAssignmentService.WheelAssignment(leader, thirdBook),
                new WheelAssignmentService.WheelAssignment(member, firstBook),
                new WheelAssignmentService.WheelAssignment(pendingMember, secondBook)
        ));

        RecruitingSchedulePlanSynchronizer planSynchronizer = new RecruitingSchedulePlanSynchronizer(
                roundRepository,
                new ScheduleCalendarService()
        );
        RecruitingScheduleAssignmentService assignmentService = new RecruitingScheduleAssignmentService(
                memberRepository,
                ownBookRepository,
                roundRepository,
                wheelStateRepository,
                wheelAssignmentService,
                wheelReassignmentService
        );
        GroupService groupService = new GroupService(
                groupRepository,
                chatRoomRepository,
                memberRepository,
                userRepository,
                passwordEncoder,
                eventPublisher,
                memberPermissionValidator,
                planSynchronizer,
                assignmentService,
                FIXED_CLOCK
        );

        groupService.updateMemberRequestStatus(
                group.getGroupId(),
                pendingMember.getMemberId(),
                "leader-user-pk",
                MemberRequestStatus.APPROVED
        );

        assertThat(pendingMember.getMemberStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(group.getTargetMemberCount()).isEqualTo(4);
        assertThat(group.getGroupRoundCount()).isEqualTo(3);
        assertThat(storedRounds).hasSize(3);
        then(wheelAssignmentService).should().assignBooks(activeMembers, books, 1);
        then(wheelAssignmentService).should().assignBooks(activeMembers, books, 2);
        then(wheelAssignmentService).should(never()).assignBooks(activeMembers, books, 3);
        then(wheelStateRepository).should().deleteByRoundIdInAndWheelState(
                storedRounds.stream().map(Round::getRoundId).toList(),
                WheelStatus.PLANNED
        );
        then(wheelReassignmentService).should().savePlannedAssignments(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(activeMembers),
                org.mockito.ArgumentMatchers.eq(books)
        );
    }

    private Member activeMember(
            String memberId,
            Group group,
            String userPK,
            int readOrder
    ) {
        return Member.builder()
                .memberId(memberId)
                .group(group)
                .user(user(userPK))
                .memberRole(MemberRole.MEMBER)
                .memberStatus(MemberStatus.ACTIVE)
                .readOrder(readOrder)
                .build();
    }

    private User user(String userPK) {
        return User.builder()
                .loginId(userPK)
                .password("password")
                .nickname(userPK)
                .mail(userPK + "@example.com")
                .isActive(true)
                .build();
    }

    private OwnBook ownBook(String ownBookId) {
        OwnBook ownBook = org.mockito.Mockito.mock(OwnBook.class);
        given(ownBook.getOwnBookId()).willReturn(ownBookId);
        return ownBook;
    }

    private Round round(Group group, int roundNumber, LocalDate startDate) {
        return Round.builder()
                .roundId("round-" + roundNumber)
                .group(group)
                .roundNumber(roundNumber)
                .startDate(startDate)
                .endDate(startDate)
                .build();
    }

    private WheelState plannedState(
            Round round,
            Member member,
            OwnBook ownBook
    ) {
        return WheelState.builder()
                .wheelStateId(round.getRoundId() + "-" + member.getMemberId())
                .roundId(round.getRoundId())
                .member(member)
                .ownBook(ownBook)
                .wheelState(WheelStatus.PLANNED)
                .isCompleted(false)
                .build();
    }
}
