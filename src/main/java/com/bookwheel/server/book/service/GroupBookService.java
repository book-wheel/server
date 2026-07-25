package com.bookwheel.server.book.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.book.dto.BookDetailsRequest;
import com.bookwheel.server.book.dto.OwnBookRegisterRequest;
import com.bookwheel.server.book.dto.OwnBookRegisterResponse;
import com.bookwheel.server.book.dto.OwnBookUpdateRequest;
import com.bookwheel.server.book.dto.OwnBookUpdateResponse;
import com.bookwheel.server.book.entity.Book;
import com.bookwheel.server.book.entity.OwnBook;
import com.bookwheel.server.book.repository.BookRepository;
import com.bookwheel.server.book.repository.OwnBookRepository;
import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupBookService {
    private final GroupRepository groupRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final OwnBookRepository ownBookRepository;

    @Transactional
    public OwnBookRegisterResponse registerOwnBook(String groupId, OwnBookRegisterRequest request, String userPK) {
        // 모임 삭제와 도서 등록이 동시에 실행되지 않도록 같은 행 잠금을 사용한다.
        Group group = findGroupByIdForUpdate(groupId);
        User user = findActiveUserById(userPK);
        Member member = findMember(groupId, userPK);

        if (member.getMemberStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.GROUP_ACTIVE_MEMBER_ONLY);
        }

        if (ownBookRepository.existsByGroup_GroupIdAndOwner_Id(groupId, userPK)) {
            throw new BusinessException(ErrorCode.OWN_BOOK_ALREADY_REGISTERED);
        }

        if (ownBookRepository.existsByGroup_GroupIdAndBook_Isbn(groupId, request.isbn())) {
            throw new BusinessException(ErrorCode.DUPLICATE_BOOK_ISBN);
        }

        Book book = findOrCreateBook(request);

        OwnBook ownBook = OwnBook.builder()
                .ownBookId(UUID.randomUUID().toString())
                .group(group)
                .owner(user)
                .book(book)
                .bookCondition(request.bookCondition())
                .noteToReader(request.noteToReader())
                .build();
        OwnBook savedOwnBook = ownBookRepository.save(ownBook);

        return OwnBookRegisterResponse.of(savedOwnBook.getOwnBookId());
    }


    private Book findOrCreateBook(BookDetailsRequest request) {

        Book book = bookRepository.findByIsbn(request.isbn())
                .orElseGet(() -> bookRepository.save(
                        Book.builder()
                                .bookId(UUID.randomUUID().toString())
                                .isbn(request.isbn())
                                .title(request.title())
                                .author(request.author())
                                .publisher(request.publisher())
                                .pubDate(request.pubDate())
                                .totalPage(request.totalPage())
                                .coverImage(request.coverImage())
                                .build()
                ));

        return book;
    }

    // 도서 등록과 모임 삭제가 동시에 실행되지 않도록 잠긴 모임을 조회한다.
    private Group findGroupByIdForUpdate(String groupId) {
        return groupRepository.findByGroupIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }

    private User findActiveUserById(String userPK) {
        User user = userRepository.findById(userPK)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessException(ErrorCode.INACTIVE_USER);
        }

        return user;
    }

    private Member findMember(String groupId, String userPK) {
        return memberRepository.findByGroup_GroupIdAndUser_Id(groupId, userPK)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_ACTIVE_MEMBER_ONLY));
    }

    @Transactional
    public OwnBookUpdateResponse updateOwnBook(String groupId, String ownBookId, OwnBookUpdateRequest request, String userPK) {
        Group group = findGroupByIdForUpdate(groupId);
        findActiveUserById(userPK);
        Member member = findMember(groupId, userPK);

        // 활성 멤버인지 확인
        if (member.getMemberStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.GROUP_ACTIVE_MEMBER_ONLY);
        }

        // 모집 중인 그룹에서만 변경 가능
        if (group.getGroupState() != State.RECRUITING) {
            throw new BusinessException(ErrorCode.GROUP_RECRUITING_STATE_REQUIRED);
        }

        // 참여 도서가 존재하는지 확인
        OwnBook ownBook = ownBookRepository.findById(ownBookId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOK_NOT_FOUND));

        // 요청 그룹과 소유자가 일치하는지 확인
        if (!ownBook.getGroup().getGroupId().equals(groupId) || !ownBook.getOwner().getId().equals(userPK)) {
            throw new BusinessException(ErrorCode.BOOK_NOT_FOUND);
        }

        // ISBN이 기존 도서와 달라졌는지 확인
        boolean isbnChanged = !ownBook.getBook().getIsbn().equals(request.isbn());

        // 변경할 도서가 그룹에 이미 등록되어 있는지 확인
        if (isbnChanged && ownBookRepository.existsByGroup_GroupIdAndBook_Isbn(groupId, request.isbn())) {
            throw new BusinessException(ErrorCode.DUPLICATE_BOOK_ISBN);
        }

        // 변경할 Book 조회 또는 생성
        Book book = findOrCreateBook(request);

        // 기존 OwnBook의 내용 변경
        ownBook.update(
                book,
                request.bookCondition(),
                request.noteToReader()
        );

        return OwnBookUpdateResponse.of(ownBook.getOwnBookId());
    }
}
