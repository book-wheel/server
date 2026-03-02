package com.bookwheel.server.community.service;

import com.bookwheel.server.book.entity.Book;
import com.bookwheel.server.book.repository.BookRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.dto.ReviewCreateRequest;
import com.bookwheel.server.community.dto.ReviewCreateResponse;
import com.bookwheel.server.community.entity.BookReview;
import com.bookwheel.server.community.repository.BookReviewRepository;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookService {
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final BookReviewRepository bookReviewRepository;


    @Transactional
    public ReviewCreateResponse createReview(String bookId, ReviewCreateRequest request, String userId) {

        Book book = bookRepository.findById(bookId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BOOK_NOT_FOUND));


        User user = userRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));


        BookReview review = BookReview.builder()
            .book(book)
            .reviewer(user)
            .content(request.comment())
            .isRecommended(request.isRecommended())
            .isHidden(request.isHidden())
            .build();

        BookReview savedReview = bookReviewRepository.save(review);

        return new ReviewCreateResponse(
            savedReview.getReviewId(),
            book.getBookId(),
            savedReview.getIsRecommended(),
            savedReview.getContent(),
            savedReview.getIsHidden(),
            savedReview.getCreatedAt()
        );
    }


}
