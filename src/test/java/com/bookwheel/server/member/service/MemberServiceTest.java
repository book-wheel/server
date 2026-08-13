package com.bookwheel.server.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bookwheel.server.book.entity.Book;
import com.bookwheel.server.book.entity.OwnBook;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.group.dto.member.GroupMemberListResponse;
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
import com.bookwheel.server.wheel.entity.WheelState;
import com.bookwheel.server.wheel.enums.WheelStatus;
import com.bookwheel.server.wheel.repository.WheelStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-14T00:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private S3Service s3Service;

    @Mock
    private RoundRepository roundRepository;

    @Mock
    private WheelStateRepository wheelStateRepository;

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(
                groupRepository,
                memberRepository,
                s3Service,
                roundRepository,
                wheelStateRepository,
                FIXED_CLOCK
        );
    }

    @RegisterExtension
    TestWatcher watcher = new TestWatcher() {
        @Override
        public void testSuccessful(ExtensionContext context) {
            System.out.println("SUCCESS: " + context.getDisplayName());
        }

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            System.out.println("FAIL: " + context.getDisplayName());
            System.out.println("이유: " + cause.getMessage());
        }
    };

    @Test
    @DisplayName("사용자가 활동 중인 모임이 있으면 true를 반환한다.")
    void isUserInGroup_ReturnsTrue_WhenActive() {
        // given (준비 단계)
        String userPK = UUID.randomUUID().toString();
        // Repository가 호출되었을 때, true를 반환하도록 세팅
        given(memberRepository.existsByUser_IdAndMemberStatus(userPK, MemberStatus.ACTIVE))
                .willReturn(true);

        // when (실행 단계)
        boolean result = memberService.isUserInGroup(userPK);

        // then (검증 단계)
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("사용자가 활동 중인 모임이 없으면 false를 반환한다.")
    void isUserInGroup_ReturnsFalse_WhenNotActive() {
        // given (준비 단계)
        String userPK = UUID.randomUUID().toString();
        // Repository가 호출되었을 때, false를 반환하도록 세팅
        given(memberRepository.existsByUser_IdAndMemberStatus(userPK, MemberStatus.ACTIVE))
                .willReturn(false);

        // when (실행 단계)
        boolean result = memberService.isUserInGroup(userPK);

        // then (검증 단계)
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("멤버 목록은 읽기 순서 수정에 필요한 memberId와 readOrder를 반환한다")
    void getGroupMembers_ReturnsMemberIdAndReadOrder() {
        String groupId = "group-1";
        Group group = Group.builder().groupId(groupId).groupName("모임").build();
        User user = User.builder()
                .loginId("reader")
                .password("password")
                .nickname("독자")
                .mail("reader@example.com")
                .build();
        Member member = Member.builder()
                .memberId("member-1")
                .group(group)
                .user(user)
                .memberRole(MemberRole.MEMBER)
                .memberStatus(MemberStatus.ACTIVE)
                .readOrder(2)
                .build();
        given(memberRepository.findByGroup_GroupIdAndMemberStatus(groupId, MemberStatus.ACTIVE))
                .willReturn(List.of(member));
        given(groupRepository.findById(groupId)).willReturn(Optional.of(group));
        given(roundRepository.findCurrentRound(groupId, LocalDate.of(2026, 8, 14), State.IN_PROGRESS))
                .willReturn(Optional.empty());

        GroupMemberListResponse response = memberService.getGroupMembers(groupId);

        assertThat(response.currentRound()).isNull();
        assertThat(response.members()).singleElement().satisfies(item -> {
            assertThat(item.memberId()).isEqualTo("member-1");
            assertThat(item.userPK()).isEqualTo(user.getId());
            assertThat(item.readOrder()).isEqualTo(2);
            assertThat(item.currentRoundAssignment()).isNull();
        });
        then(wheelStateRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("멤버 목록은 현재 라운드와 멤버별 배정 도서 및 독서 상태를 반환한다")
    void getGroupMembers_ReturnsCurrentRoundAssignment() {
        String groupId = "group-1";
        Group group = Group.builder().groupId(groupId).groupName("모임").build();
        User reader = createUser("reader", "독자");
        User owner = createUser("owner", "책 주인");
        Member member = Member.builder()
                .memberId("member-1")
                .group(group)
                .user(reader)
                .memberRole(MemberRole.MEMBER)
                .memberStatus(MemberStatus.ACTIVE)
                .readOrder(1)
                .build();
        Round currentRound = Round.builder()
                .roundId("round-2")
                .group(group)
                .roundNumber(2)
                .startDate(LocalDate.of(2026, 8, 10))
                .endDate(LocalDate.of(2026, 8, 16))
                .build();
        Book book = Book.builder()
                .bookId("book-1")
                .title("소년이 온다")
                .coverImage("https://example.com/cover.jpg")
                .build();
        OwnBook ownBook = OwnBook.builder()
                .ownBookId("own-book-1")
                .group(group)
                .owner(owner)
                .book(book)
                .build();
        WheelState assignment = WheelState.builder()
                .wheelStateId("wheel-1")
                .roundId(currentRound.getRoundId())
                .member(member)
                .ownBook(ownBook)
                .wheelState(WheelStatus.READING)
                .build();

        given(roundRepository.findCurrentRound(
                groupId,
                LocalDate.of(2026, 8, 14),
                State.IN_PROGRESS
        )).willReturn(Optional.of(currentRound));
        given(groupRepository.findById(groupId)).willReturn(Optional.of(group));
        given(wheelStateRepository.findAllByRoundIdWithMemberAndBook(currentRound.getRoundId()))
                .willReturn(List.of(assignment));
        given(memberRepository.findByGroup_GroupIdAndMemberStatus(groupId, MemberStatus.ACTIVE))
                .willReturn(List.of(member));

        GroupMemberListResponse response = memberService.getGroupMembers(groupId);

        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.currentRound()).satisfies(round -> {
            assertThat(round.roundId()).isEqualTo("round-2");
            assertThat(round.roundNumber()).isEqualTo(2);
        });
        assertThat(response.members()).singleElement().satisfies(item -> {
            assertThat(item.readOrder()).isEqualTo(1);
            assertThat(item.currentRoundAssignment()).satisfies(currentAssignment -> {
                assertThat(currentAssignment.wheelStateId()).isEqualTo("wheel-1");
                assertThat(currentAssignment.bookId()).isEqualTo("book-1");
                assertThat(currentAssignment.bookTitle()).isEqualTo("소년이 온다");
                assertThat(currentAssignment.coverImage()).isEqualTo("https://example.com/cover.jpg");
                assertThat(currentAssignment.readingStatus()).isEqualTo(WheelStatus.READING);
            });
        });
    }

    @Test
    @DisplayName("현재 라운드에 배정되지 않은 멤버의 배정 정보는 null이다")
    void getGroupMembers_ReturnsNullAssignment_WhenMemberIsNotAssigned() {
        String groupId = "group-1";
        Group group = Group.builder().groupId(groupId).groupName("모임").build();
        Member member = Member.builder()
                .memberId("member-1")
                .group(group)
                .user(createUser("reader", "독자"))
                .memberRole(MemberRole.MEMBER)
                .memberStatus(MemberStatus.ACTIVE)
                .readOrder(1)
                .build();
        Round currentRound = Round.builder()
                .roundId("round-1")
                .group(group)
                .roundNumber(1)
                .startDate(LocalDate.of(2026, 8, 10))
                .endDate(LocalDate.of(2026, 8, 16))
                .build();

        given(roundRepository.findCurrentRound(
                groupId,
                LocalDate.of(2026, 8, 14),
                State.IN_PROGRESS
        )).willReturn(Optional.of(currentRound));
        given(groupRepository.findById(groupId)).willReturn(Optional.of(group));
        given(wheelStateRepository.findAllByRoundIdWithMemberAndBook(currentRound.getRoundId()))
                .willReturn(List.of());
        given(memberRepository.findByGroup_GroupIdAndMemberStatus(groupId, MemberStatus.ACTIVE))
                .willReturn(List.of(member));

        GroupMemberListResponse response = memberService.getGroupMembers(groupId);

        assertThat(response.currentRound()).isNotNull();
        assertThat(response.members()).singleElement().satisfies(item ->
                assertThat(item.currentRoundAssignment()).isNull()
        );
    }

    @Test
    @DisplayName("존재하지 않는 그룹의 멤버 목록은 조회할 수 없다")
    void getGroupMembers_ThrowsGroupNotFound_WhenGroupDoesNotExist() {
        String groupId = "not-existing-group";
        given(groupRepository.findById(groupId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.getGroupMembers(groupId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_NOT_FOUND);

        then(roundRepository).shouldHaveNoInteractions();
        then(wheelStateRepository).shouldHaveNoInteractions();
        then(memberRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("삭제된 그룹의 멤버 목록은 조회할 수 없다")
    void getGroupMembers_ThrowsGroupNotFound_WhenGroupIsDeleted() {
        String groupId = "deleted-group";
        Group deletedGroup = Group.builder()
                .groupId(groupId)
                .groupName("삭제된 모임")
                .groupState(State.DELETED)
                .build();
        given(groupRepository.findById(groupId)).willReturn(Optional.of(deletedGroup));

        assertThatThrownBy(() -> memberService.getGroupMembers(groupId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_NOT_FOUND);

        then(roundRepository).shouldHaveNoInteractions();
        then(wheelStateRepository).shouldHaveNoInteractions();
        then(memberRepository).shouldHaveNoInteractions();
    }

    private User createUser(String loginId, String nickname) {
        return User.builder()
                .loginId(loginId)
                .password("password")
                .nickname(nickname)
                .mail(loginId + "@example.com")
                .build();
    }
}
