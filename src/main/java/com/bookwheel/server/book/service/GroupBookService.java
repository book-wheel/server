package com.bookwheel.server.book.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.book.dto.OwnBookRegisterRequest;
import com.bookwheel.server.book.dto.OwnBookRegisterResponse;
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
    public OwnBookRegisterResponse registerOwnBook(String groupId, OwnBookRegisterRequest request, String userId) {
        Group group = findGroupById(groupId);
        User user = findActiveUserById(userId);
        Member member = findMember(groupId, userId);

        if (member.getMemberStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.GROUP_ACTIVE_MEMBER_ONLY);
        }

        if (ownBookRepository.existsByGroup_GroupIdAndOwner_Id(groupId, user.getId())) {
            throw new BusinessException(ErrorCode.OWN_BOOK_ALREADY_REGISTERED);
        }

        if (ownBookRepository.existsByGroup_GroupIdAndBook_Isbn(groupId, request.isbn())) {
            throw new BusinessException(ErrorCode.DUPLICATE_BOOK_ISBN);
        }

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

    private Group findGroupById(String groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }

    private User findActiveUserById(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessException(ErrorCode.INACTIVE_USER);
        }

        return user;
    }

    private Member findMember(String groupId, String userId) {
        return memberRepository.findByGroup_GroupIdAndUser_Id(groupId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_ACTIVE_MEMBER_ONLY));
    }
}
