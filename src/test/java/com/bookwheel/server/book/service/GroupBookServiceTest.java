package com.bookwheel.server.book.service;

import com.bookwheel.server.book.entity.Book;
import com.bookwheel.server.book.entity.OwnBook;
import com.bookwheel.server.book.repository.BookRepository;
import com.bookwheel.server.book.repository.OwnBookRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberRole;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.schedule.service.RecruitingScheduleAssignmentService;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class GroupBookServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private OwnBookRepository ownBookRepository;

    @Mock
    private RecruitingScheduleAssignmentService recruitingScheduleAssignmentService;

    @InjectMocks
    private GroupBookService groupBookService;

    @Test
    void deleteOwnBook_DeletesOwnedBookInRecruitingGroup() {
        Group group = group("group-1", State.RECRUITING);
        User user = user("owner");
        Member member = member(group, user, MemberStatus.ACTIVE);
        OwnBook ownBook = ownBook("own-book-1", group, user);

        given(groupRepository.findByGroupIdForUpdate(group.getGroupId())).willReturn(Optional.of(group));
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
        given(memberRepository.findByGroup_GroupIdAndUser_Id(group.getGroupId(), user.getId()))
                .willReturn(Optional.of(member));
        given(ownBookRepository.findById(ownBook.getOwnBookId())).willReturn(Optional.of(ownBook));

        groupBookService.deleteOwnBook(group.getGroupId(), ownBook.getOwnBookId(), user.getId());

        then(ownBookRepository).should().delete(ownBook);
    }

    @Test
    void deleteOwnBook_RejectsBookOwnedByAnotherUser() {
        Group group = group("group-1", State.RECRUITING);
        User user = user("requester");
        User owner = user("owner");
        Member member = member(group, user, MemberStatus.ACTIVE);
        OwnBook ownBook = ownBook("own-book-1", group, owner);

        given(groupRepository.findByGroupIdForUpdate(group.getGroupId())).willReturn(Optional.of(group));
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
        given(memberRepository.findByGroup_GroupIdAndUser_Id(group.getGroupId(), user.getId()))
                .willReturn(Optional.of(member));
        given(ownBookRepository.findById(ownBook.getOwnBookId())).willReturn(Optional.of(ownBook));

        assertThatThrownBy(() ->
                groupBookService.deleteOwnBook(group.getGroupId(), ownBook.getOwnBookId(), user.getId())
        )
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.BOOK_NOT_FOUND);

        then(ownBookRepository).should(never()).delete(ownBook);
    }

    @Test
    void deleteOwnBook_RejectsInProgressGroup() {
        Group group = group("group-1", State.IN_PROGRESS);
        User user = user("owner");
        Member member = member(group, user, MemberStatus.ACTIVE);

        given(groupRepository.findByGroupIdForUpdate(group.getGroupId())).willReturn(Optional.of(group));
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
        given(memberRepository.findByGroup_GroupIdAndUser_Id(group.getGroupId(), user.getId()))
                .willReturn(Optional.of(member));

        assertThatThrownBy(() ->
                groupBookService.deleteOwnBook(group.getGroupId(), "own-book-1", user.getId())
        )
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_RECRUITING_STATE_REQUIRED);

        then(ownBookRepository).should(never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteOwnBook_RejectsMissingBook() {
        Group group = group("group-1", State.RECRUITING);
        User user = user("owner");
        Member member = member(group, user, MemberStatus.ACTIVE);

        given(groupRepository.findByGroupIdForUpdate(group.getGroupId())).willReturn(Optional.of(group));
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
        given(memberRepository.findByGroup_GroupIdAndUser_Id(group.getGroupId(), user.getId()))
                .willReturn(Optional.of(member));
        given(ownBookRepository.findById("missing-own-book")).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                groupBookService.deleteOwnBook(group.getGroupId(), "missing-own-book", user.getId())
        )
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.BOOK_NOT_FOUND);

        then(ownBookRepository).should(never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteOwnBook_RejectsInactiveMember() {
        Group group = group("group-1", State.RECRUITING);
        User user = user("owner");
        Member member = member(group, user, MemberStatus.EXITED);

        given(groupRepository.findByGroupIdForUpdate(group.getGroupId())).willReturn(Optional.of(group));
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
        given(memberRepository.findByGroup_GroupIdAndUser_Id(group.getGroupId(), user.getId()))
                .willReturn(Optional.of(member));

        assertThatThrownBy(() ->
                groupBookService.deleteOwnBook(group.getGroupId(), "own-book-1", user.getId())
        )
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GROUP_ACTIVE_MEMBER_ONLY);

        then(ownBookRepository).should(never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteOwnBook_RejectsBookFromAnotherGroup() {
        Group requestedGroup = group("group-1", State.RECRUITING);
        Group bookGroup = group("group-2", State.RECRUITING);
        User user = user("owner");
        Member member = member(requestedGroup, user, MemberStatus.ACTIVE);
        OwnBook ownBook = ownBook("own-book-1", bookGroup, user);

        given(groupRepository.findByGroupIdForUpdate(requestedGroup.getGroupId()))
                .willReturn(Optional.of(requestedGroup));
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
        given(memberRepository.findByGroup_GroupIdAndUser_Id(requestedGroup.getGroupId(), user.getId()))
                .willReturn(Optional.of(member));
        given(ownBookRepository.findById(ownBook.getOwnBookId())).willReturn(Optional.of(ownBook));

        assertThatThrownBy(() ->
                groupBookService.deleteOwnBook(requestedGroup.getGroupId(), ownBook.getOwnBookId(), user.getId())
        )
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.BOOK_NOT_FOUND);

        then(ownBookRepository).should(never()).delete(ownBook);
    }

    private Group group(String groupId, State state) {
        return Group.builder()
                .groupId(groupId)
                .groupName("모임")
                .groupState(state)
                .build();
    }

    private User user(String nickname) {
        return User.builder()
                .loginId(nickname + "-login")
                .nickname(nickname)
                .mail(nickname + "@example.com")
                .isActive(true)
                .build();
    }

    private Member member(Group group, User user, MemberStatus status) {
        return Member.builder()
                .memberId("member-" + user.getId())
                .group(group)
                .user(user)
                .memberRole(MemberRole.MEMBER)
                .memberStatus(status)
                .build();
    }

    private OwnBook ownBook(String ownBookId, Group group, User owner) {
        Book book = Book.builder()
                .bookId("book-1")
                .isbn("9791190090018")
                .title("책")
                .build();

        return OwnBook.builder()
                .ownBookId(ownBookId)
                .group(group)
                .owner(owner)
                .book(book)
                .build();
    }
}
