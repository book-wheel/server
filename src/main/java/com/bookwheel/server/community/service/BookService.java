package com.bookwheel.server.community.service;

import com.bookwheel.server.book.entity.Book;
import com.bookwheel.server.book.repository.BookRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.dto.ReviewCreateRequest;
import com.bookwheel.server.community.dto.ReviewCreateResponse;
import com.bookwheel.server.community.dto.ReviewDetailResponse;
import com.bookwheel.server.community.dto.ReviewStatsResponse;
import com.bookwheel.server.community.entity.BookReview;
import com.bookwheel.server.community.entity.ReviewLike;
import com.bookwheel.server.community.repository.BookReviewRepository;
import com.bookwheel.server.community.repository.ReviewLikeRepository;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookService {
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final BookReviewRepository bookReviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;


    @Transactional
    public ReviewCreateResponse createReview(String bookId, ReviewCreateRequest request, String userId) {

        Book book = bookRepository.findById(bookId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BOOK_NOT_FOUND));

        if (bookReviewRepository.existsByBookAndReviewer_UserId(book, userId)) {
            throw new BusinessException(ErrorCode.ALREADY_REVIEWED);
        }


        User user = userRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));


        BookReview review = request.toEntity(book, user);

        BookReview savedReview = bookReviewRepository.save(review);

        return ReviewCreateResponse.from(savedReview);
    }

    @Transactional
    public void toggleReviewLike(Long reviewId, String userId) {
        BookReview review = bookReviewRepository.findById(reviewId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        User user = userRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        reviewLikeRepository.findByReviewAndUser(review, user)
            .ifPresentOrElse(
                // 이미 하트를 눌렀던 상태면 -> 좋아요 취소
                reviewLike -> {
                    reviewLikeRepository.delete(reviewLike);
                    review.decreaseLikeCount();
                },
                // 하트를 누르지 않은 상태면 -> 좋아요 추가
                () -> {
                    reviewLikeRepository.save(ReviewLike.create(review, user));
                    review.increaseLikeCount();
                }
            );
    }

    public ReviewStatsResponse getReviewStats(String bookId) {
        Book book = bookRepository.findById(bookId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BOOK_NOT_FOUND));

        long recommendedCount = bookReviewRepository.countByBookAndIsRecommended(book, true);
        long notRecommendedCount = bookReviewRepository.countByBookAndIsRecommended(book, false);
        long totalCount = recommendedCount + notRecommendedCount;

        if (totalCount == 0) {
            return new ReviewStatsResponse(0, 0); // 리뷰가 없을 때
        }

        int recommendedRatio = (int) ((recommendedCount * 100) / totalCount);
        int notRecommendedRatio = 100 - recommendedRatio;

        return new ReviewStatsResponse(recommendedRatio, notRecommendedRatio);
    }

    public List<ReviewDetailResponse> getReviewList(String bookId, String userId) {
        Book book = bookRepository.findById(bookId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BOOK_NOT_FOUND));

        User user = userRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));


        List<BookReview> reviews = bookReviewRepository.findAllByBookOrderByCreatedAtDesc(book);


        return reviews.stream().map(review -> {

            boolean isLikedByMe = reviewLikeRepository.existsByReviewAndUser(review, user);

            return ReviewDetailResponse.of(review, isLikedByMe);
        }).collect(Collectors.toList());
    }


}
