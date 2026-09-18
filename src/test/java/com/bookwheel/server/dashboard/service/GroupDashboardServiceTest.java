package com.bookwheel.server.dashboard.service;

import com.bookwheel.server.book.entity.Book;
import com.bookwheel.server.book.entity.OwnBook;
import com.bookwheel.server.book.repository.OwnBookRepository;
import com.bookwheel.server.dashboard.dto.DashboardResponse;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.group.repository.GroupRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GroupDashboardServiceTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-28T03:00:00Z"),
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
    private WheelStateRepository wheelStateRepository;
    @Mock
    private RoundRepository roundRepository;

    private GroupDashboardService service;

    @BeforeEach
    void setUp() {
        service = new GroupDashboardService(
                groupRepository,
                memberRepository,
                userRepository,
                ownBookRepository,
                wheelStateRepository,
                roundRepository,
                FIXED_CLOCK
        );
    }

    @Test
    @DisplayName("시작 조건을 충족하지 못한 모집 중 일정 틀은 현재 라운드로 노출하지 않는다")
    void getDashboard_DoesNotExposeRecruitingRoundAsCurrent() {
        String groupId = "group-1";
        User user = User.builder()
                .loginId("login")
                .password("password")
                .nickname("멤버")
                .mail("member@example.com")
                .isActive(true)
                .build();
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("독서 모임")
                .groupState(State.RECRUITING)
                .startDate(LocalDate.now(FIXED_CLOCK))
                .groupRoundCount(9)
                .build();
        Member member = Member.builder()
                .memberId("member-1")
                .group(group)
                .user(user)
                .memberRole(MemberRole.MEMBER)
                .memberStatus(MemberStatus.ACTIVE)
                .build();
        Round storedRoundShell = Round.builder()
                .roundId("round-1")
                .group(group)
                .roundNumber(1)
                .startDate(LocalDate.now(FIXED_CLOCK))
                .endDate(LocalDate.now(FIXED_CLOCK).plusDays(6))
                .build();

        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
        given(groupRepository.findById(groupId)).willReturn(Optional.of(group));
        given(memberRepository.findByGroup_GroupIdAndUser_Id(groupId, user.getId()))
                .willReturn(Optional.of(member));
        given(roundRepository.findByGroup_GroupIdAndStartDateIsNotNullAndEndDateIsNotNullOrderByRoundNumberAsc(groupId))
                .willReturn(List.of(storedRoundShell));
        given(wheelStateRepository.findFirstByRoundIdAndMember_MemberId("round-1", "member-1"))
                .willReturn(Optional.empty());
        given(ownBookRepository.findByGroup_GroupIdAndOwner_Id(groupId, user.getId()))
                .willReturn(Optional.empty());

        DashboardResponse response = service.getDashboard(groupId, user.getId());

        assertThat(response.currentRound()).isZero();
        assertThat(response.startDate()).isEqualTo(group.getStartDate());
        assertThat(response.endDate()).isEqualTo(group.getStartDate());
        assertThat(response.myStep()).isNull();
    }

    @Test
    @DisplayName("대시보드 myStep은 직전 전달자와 원래 책 소유자를 구분해 반환한다")
    void getDashboard_ReturnsSenderAndOwnerNicknamesSeparately() {
        String groupId = "group-1";
        LocalDate today = LocalDate.now(FIXED_CLOCK);
        User me = activeUser("현재 독자");
        User previousSender = activeUser("직전 전달자");
        User owner = activeUser("원래 책 주인");
        Group group = Group.builder()
                .groupId(groupId)
                .groupName("독서 모임")
                .groupState(State.IN_PROGRESS)
                .groupRoundCount(2)
                .build();
        Member currentMember = Member.builder()
                .memberId("member-current")
                .group(group)
                .user(me)
                .memberRole(MemberRole.MEMBER)
                .memberStatus(MemberStatus.ACTIVE)
                .build();
        Member previousMember = Member.builder()
                .memberId("member-previous")
                .group(group)
                .user(previousSender)
                .memberRole(MemberRole.MEMBER)
                .memberStatus(MemberStatus.ACTIVE)
                .build();
        Round previousRound = Round.builder()
                .roundId("round-1")
                .group(group)
                .roundNumber(1)
                .startDate(today.minusDays(7))
                .endDate(today.minusDays(1))
                .build();
        Round currentRound = Round.builder()
                .roundId("round-2")
                .group(group)
                .roundNumber(2)
                .startDate(today)
                .endDate(today.plusDays(6))
                .build();
        OwnBook ownBook = OwnBook.builder()
                .ownBookId("own-book-1")
                .group(group)
                .owner(owner)
                .book(Book.builder()
                        .bookId("book-1")
                        .title("소년이 온다")
                        .coverImage("https://example.com/book.jpg")
                        .build())
                .build();
        WheelState previousState = WheelState.builder()
                .wheelStateId("wheel-1")
                .roundId(previousRound.getRoundId())
                .member(previousMember)
                .ownBook(ownBook)
                .wheelState(WheelStatus.COMPLETED)
                .build();
        WheelState currentState = WheelState.builder()
                .wheelStateId("wheel-2")
                .roundId(currentRound.getRoundId())
                .member(currentMember)
                .ownBook(ownBook)
                .wheelState(WheelStatus.READY)
                .build();

        given(userRepository.findById(me.getId())).willReturn(Optional.of(me));
        given(groupRepository.findById(groupId)).willReturn(Optional.of(group));
        given(memberRepository.findByGroup_GroupIdAndUser_Id(groupId, me.getId()))
                .willReturn(Optional.of(currentMember));
        given(roundRepository.findByGroup_GroupIdAndStartDateIsNotNullAndEndDateIsNotNullOrderByRoundNumberAsc(groupId))
                .willReturn(List.of(previousRound, currentRound));
        given(wheelStateRepository.findFirstByRoundIdAndMember_MemberId("round-2", "member-current"))
                .willReturn(Optional.of(currentState));
        given(roundRepository.findByGroup_GroupIdAndRoundNumber(groupId, 1))
                .willReturn(Optional.of(previousRound));
        given(wheelStateRepository.findFirstByRoundIdAndOwnBook_OwnBookId("round-1", "own-book-1"))
                .willReturn(Optional.of(previousState));
        given(ownBookRepository.findByGroup_GroupIdAndOwner_Id(groupId, me.getId()))
                .willReturn(Optional.empty());

        DashboardResponse response = service.getDashboard(groupId, me.getId());

        assertThat(response.myStep()).satisfies(myStep -> {
            assertThat(myStep.senderNickname()).isEqualTo("직전 전달자");
            assertThat(myStep.ownerNickname()).isEqualTo("원래 책 주인");
        });
    }

    private User activeUser(String nickname) {
        return User.builder()
                .loginId(nickname + "-login")
                .password("password")
                .nickname(nickname)
                .mail(nickname + "@example.com")
                .isActive(true)
                .build();
    }
}
