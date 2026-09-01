package com.bookwheel.server.dashboard.service;

import com.bookwheel.server.book.entity.Book;
import com.bookwheel.server.book.entity.OwnBook;
import com.bookwheel.server.book.repository.OwnBookRepository;
import com.bookwheel.server.dashboard.dto.GroupReadingCardResponse;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.Region;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberRole;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.schedule.entity.Round;
import com.bookwheel.server.schedule.repository.RoundRepository;
import com.bookwheel.server.user.entity.User;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class GroupReadingCardServiceTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 27);
    private static final String USER_PK = "user-pk";

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private OwnBookRepository ownBookRepository;
    @Mock
    private RoundRepository roundRepository;
    @Mock
    private WheelStateRepository wheelStateRepository;

    private GroupReadingCardService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay(KST).toInstant(), KST);
        service = new GroupReadingCardService(
                memberRepository,
                ownBookRepository,
                roundRepository,
                wheelStateRepository,
                clock
        );
    }

    @Test
    @DisplayName("배정과 등록 도서가 없는 모집 중 모임도 예정 카드로 반환한다")
    void getReadingCards_ReturnsRecruitingGroupWithoutBooks() {
        Group group = group("group-1", State.RECRUITING, TODAY.plusDays(14));
        Member membership = member("member-1", group, user("나"));
        givenMemberships(membership);
        given(roundRepository.findExecutableRoundsByGroupIds(List.of("group-1"))).willReturn(List.of());
        given(ownBookRepository.findAllByOwnerIdAndGroupIdInWithBook(USER_PK, List.of("group-1")))
                .willReturn(List.of());

        List<GroupReadingCardResponse> responses = service.getReadingCards(USER_PK);

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.groupId()).isEqualTo("group-1");
            assertThat(response.status()).isEqualTo("scheduled");
            assertThat(response.currentRound()).isZero();
            assertThat(response.startDate()).isEqualTo(TODAY.plusDays(14));
            assertThat(response.endDate()).isEqualTo(TODAY.plusDays(14));
            assertThat(response.dDay()).isEqualTo(14);
            assertThat(response.myStep()).isNull();
            assertThat(response.myBookStep()).isNull();
        });
        then(wheelStateRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("모집 중 모임은 유효한 첫 회차의 PLANNED 배정과 등록 도서를 함께 반환한다")
    void getReadingCards_ReturnsPlannedAssignmentAndRegisteredBook() {
        User me = user("나");
        User sender = user("책 주인");
        User holder = user("내 책 독자");
        Group group = group("group-1", State.RECRUITING, TODAY.plusDays(7));
        Member myMembership = member("member-me", group, me);
        Member holderMembership = member("member-holder", group, holder);
        Round firstRound = round("round-1", group, 1, TODAY.plusDays(7), TODAY.plusDays(13));
        OwnBook bookToRead = ownBook("own-incoming", group, sender, book("book-incoming", "읽을 책"));
        OwnBook myOwnBook = ownBook("own-mine", group, me, book("book-mine", "등록한 책"));
        WheelState myAssignment = wheelState(
                "wheel-incoming", firstRound, myMembership, bookToRead, WheelStatus.PLANNED
        );
        WheelState myBookAssignment = wheelState(
                "wheel-mine", firstRound, holderMembership, myOwnBook, WheelStatus.PLANNED
        );

        givenMemberships(myMembership);
        given(roundRepository.findExecutableRoundsByGroupIds(List.of("group-1")))
                .willReturn(List.of(firstRound));
        given(ownBookRepository.findAllByOwnerIdAndGroupIdInWithBook(USER_PK, List.of("group-1")))
                .willReturn(List.of(myOwnBook));
        given(wheelStateRepository.findAllByRoundIdInWithReadingCardDetails(anyCollection()))
                .willReturn(List.of(myAssignment, myBookAssignment));

        GroupReadingCardResponse response = service.getReadingCards(USER_PK).get(0);

        assertThat(response.myStep()).satisfies(myStep -> {
            assertThat(myStep.bookId()).isEqualTo("book-incoming");
            assertThat(myStep.status()).isEqualTo(WheelStatus.PLANNED);
            assertThat(myStep.senderNickname()).isEqualTo("책 주인");
        });
        assertThat(response.myBookStep()).satisfies(myBookStep -> {
            assertThat(myBookStep.bookId()).isEqualTo("book-mine");
            assertThat(myBookStep.holderNickname()).isEqualTo("내 책 독자");
            assertThat(myBookStep.status()).isEqualTo(WheelStatus.PLANNED);
            assertThat(myBookStep.location()).isNull();
        });
    }

    @Test
    @DisplayName("첫 회차 시작일이 현재 모임 시작일과 다르면 이전 배정을 노출하지 않는다")
    void getReadingCards_DoesNotExposeStalePlannedAssignment() {
        User me = user("나");
        Group group = group("group-1", State.RECRUITING, TODAY.plusDays(7));
        Member membership = member("member-me", group, me);
        Round staleRound = round("round-stale", group, 1, TODAY.plusDays(8), TODAY.plusDays(14));
        OwnBook myOwnBook = ownBook("own-mine", group, me, book("book-mine", "등록한 책"));

        givenMemberships(membership);
        given(roundRepository.findExecutableRoundsByGroupIds(List.of("group-1")))
                .willReturn(List.of(staleRound));
        given(ownBookRepository.findAllByOwnerIdAndGroupIdInWithBook(USER_PK, List.of("group-1")))
                .willReturn(List.of(myOwnBook));

        GroupReadingCardResponse response = service.getReadingCards(USER_PK).get(0);

        assertThat(response.myStep()).isNull();
        assertThat(response.myBookStep()).satisfies(myBookStep -> {
            assertThat(myBookStep.bookId()).isEqualTo("book-mine");
            assertThat(myBookStep.holderNickname()).isNull();
            assertThat(myBookStep.status()).isNull();
        });
        then(wheelStateRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("모집 중 모임의 첫 회차 배정이 PLANNED가 아니면 myStep을 반환하지 않는다")
    void getReadingCards_DoesNotExposeNonPlannedPreStartAssignment() {
        User me = user("나");
        User sender = user("책 주인");
        Group group = group("group-1", State.RECRUITING, TODAY.plusDays(7));
        Member membership = member("member-me", group, me);
        Round firstRound = round("round-1", group, 1, TODAY.plusDays(7), TODAY.plusDays(13));
        OwnBook bookToRead = ownBook("own-incoming", group, sender, book("book-incoming", "잘못된 상태의 책"));
        WheelState readyAssignment = wheelState(
                "wheel-ready",
                firstRound,
                membership,
                bookToRead,
                WheelStatus.READY
        );

        givenMemberships(membership);
        given(roundRepository.findExecutableRoundsByGroupIds(List.of("group-1")))
                .willReturn(List.of(firstRound));
        given(ownBookRepository.findAllByOwnerIdAndGroupIdInWithBook(USER_PK, List.of("group-1")))
                .willReturn(List.of());
        given(wheelStateRepository.findAllByRoundIdInWithReadingCardDetails(anyCollection()))
                .willReturn(List.of(readyAssignment));

        GroupReadingCardResponse response = service.getReadingCards(USER_PK).get(0);

        assertThat(response.myStep()).isNull();
        assertThat(response.myBookStep()).isNull();
    }

    @Test
    @DisplayName("모임 시작일이 지난 모집 중 모임은 재일정 필요 상태와 null D-day를 반환한다")
    void getReadingCards_ReturnsRescheduleRequired() {
        Group group = group("group-1", State.RECRUITING, TODAY.minusDays(1));
        Member membership = member("member-1", group, user("나"));
        givenMemberships(membership);
        given(roundRepository.findExecutableRoundsByGroupIds(List.of("group-1"))).willReturn(List.of());
        given(ownBookRepository.findAllByOwnerIdAndGroupIdInWithBook(USER_PK, List.of("group-1")))
                .willReturn(List.of());

        GroupReadingCardResponse response = service.getReadingCards(USER_PK).get(0);

        assertThat(response.status()).isEqualTo("reschedule_required");
        assertThat(response.dDay()).isNull();
    }

    @Test
    @DisplayName("일정이 없는 모집 중 모임도 날짜와 D-day가 null인 카드로 반환한다")
    void getReadingCards_ReturnsRecruitingGroupWithoutSchedule() {
        Group group = group("group-1", State.RECRUITING, null);
        Member membership = member("member-1", group, user("나"));
        givenMemberships(membership);
        given(roundRepository.findExecutableRoundsByGroupIds(List.of("group-1"))).willReturn(List.of());
        given(ownBookRepository.findAllByOwnerIdAndGroupIdInWithBook(USER_PK, List.of("group-1")))
                .willReturn(List.of());

        GroupReadingCardResponse response = service.getReadingCards(USER_PK).get(0);

        assertThat(response.status()).isEqualTo("scheduled");
        assertThat(response.startDate()).isNull();
        assertThat(response.endDate()).isNull();
        assertThat(response.dDay()).isNull();
    }

    @Test
    @DisplayName("진행 중 모임은 현재 회차의 두 책과 직전 전달자를 반환한다")
    void getReadingCards_ReturnsCurrentRoundBooks() {
        User me = user("나");
        User owner = user("원래 책 주인");
        User previousHolder = user("직전 전달자");
        User currentHolder = user("현재 독자");
        Group group = Group.builder()
                .groupId("group-1")
                .groupName("진행 모임")
                .groupState(State.IN_PROGRESS)
                .groupRoundCount(3)
                .groupOffline(true)
                .groupRegion(Region.SEOUL)
                .build();
        Member myMembership = member("member-me", group, me);
        Member previousMembership = member("member-previous", group, previousHolder);
        Member currentHolderMembership = member("member-current-holder", group, currentHolder);
        Round previousRound = round("round-1", group, 1, TODAY.minusDays(7), TODAY.minusDays(1));
        Round currentRound = round("round-2", group, 2, TODAY, TODAY.plusDays(6));
        OwnBook bookToRead = ownBook("own-incoming", group, owner, book("book-incoming", "읽는 중인 책"));
        OwnBook myOwnBook = ownBook("own-mine", group, me, book("book-mine", "내가 등록한 책"));
        WheelState previousAssignment = wheelState(
                "wheel-previous", previousRound, previousMembership, bookToRead, WheelStatus.COMPLETED
        );
        WheelState currentAssignment = wheelState(
                "wheel-current", currentRound, myMembership, bookToRead, WheelStatus.READY
        );
        WheelState myBookAssignment = wheelState(
                "wheel-mine", currentRound, currentHolderMembership, myOwnBook, WheelStatus.READING
        );

        givenMemberships(myMembership);
        given(roundRepository.findExecutableRoundsByGroupIds(List.of("group-1")))
                .willReturn(List.of(previousRound, currentRound));
        given(ownBookRepository.findAllByOwnerIdAndGroupIdInWithBook(USER_PK, List.of("group-1")))
                .willReturn(List.of(myOwnBook));
        given(wheelStateRepository.findAllByRoundIdInWithReadingCardDetails(anyCollection()))
                .willReturn(List.of(previousAssignment, currentAssignment, myBookAssignment));

        GroupReadingCardResponse response = service.getReadingCards(USER_PK).get(0);

        assertThat(response.status()).isEqualTo("active");
        assertThat(response.currentRound()).isEqualTo(2);
        assertThat(response.startDate()).isEqualTo(TODAY);
        assertThat(response.endDate()).isEqualTo(TODAY.plusDays(6));
        assertThat(response.dDay()).isEqualTo(6);
        assertThat(response.myStep()).satisfies(myStep -> {
            assertThat(myStep.status()).isEqualTo(WheelStatus.READY);
            assertThat(myStep.senderNickname()).isEqualTo("직전 전달자");
        });
        assertThat(response.myBookStep()).satisfies(myBookStep -> {
            assertThat(myBookStep.status()).isEqualTo(WheelStatus.READING);
            assertThat(myBookStep.holderNickname()).isEqualTo("현재 독자");
            assertThat(myBookStep.location()).isEqualTo("서울");
        });
    }

    @Test
    @DisplayName("활성 예정·진행 모임이 없으면 빈 목록을 반환한다")
    void getReadingCards_ReturnsEmptyList() {
        given(memberRepository.findReadingCardMemberships(
                USER_PK,
                MemberStatus.ACTIVE,
                List.of(State.RECRUITING, State.IN_PROGRESS)
        )).willReturn(List.of());

        assertThat(service.getReadingCards(USER_PK)).isEmpty();

        then(roundRepository).shouldHaveNoInteractions();
        then(ownBookRepository).shouldHaveNoInteractions();
        then(wheelStateRepository).shouldHaveNoInteractions();
    }

    private void givenMemberships(Member... memberships) {
        given(memberRepository.findReadingCardMemberships(
                USER_PK,
                MemberStatus.ACTIVE,
                List.of(State.RECRUITING, State.IN_PROGRESS)
        )).willReturn(List.of(memberships));
    }

    private Group group(String groupId, State state, LocalDate startDate) {
        return Group.builder()
                .groupId(groupId)
                .groupName("독서 모임")
                .groupState(state)
                .startDate(startDate)
                .groupRoundCount(5)
                .build();
    }

    private User user(String nickname) {
        return User.builder()
                .loginId(nickname + "-login")
                .password("password")
                .nickname(nickname)
                .mail(nickname + "@example.com")
                .isActive(true)
                .build();
    }

    private Member member(String memberId, Group group, User user) {
        return Member.builder()
                .memberId(memberId)
                .group(group)
                .user(user)
                .memberRole(MemberRole.MEMBER)
                .memberStatus(MemberStatus.ACTIVE)
                .build();
    }

    private Round round(
            String roundId,
            Group group,
            int roundNumber,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return Round.builder()
                .roundId(roundId)
                .group(group)
                .roundNumber(roundNumber)
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }

    private Book book(String bookId, String title) {
        return Book.builder()
                .bookId(bookId)
                .isbn(bookId + "-isbn")
                .title(title)
                .author("작가")
                .coverImage("https://example.com/" + bookId + ".jpg")
                .build();
    }

    private OwnBook ownBook(String ownBookId, Group group, User owner, Book book) {
        return OwnBook.builder()
                .ownBookId(ownBookId)
                .group(group)
                .owner(owner)
                .book(book)
                .build();
    }

    private WheelState wheelState(
            String wheelStateId,
            Round round,
            Member member,
            OwnBook ownBook,
            WheelStatus status
    ) {
        return WheelState.builder()
                .wheelStateId(wheelStateId)
                .roundId(round.getRoundId())
                .member(member)
                .ownBook(ownBook)
                .wheelState(status)
                .build();
    }
}
