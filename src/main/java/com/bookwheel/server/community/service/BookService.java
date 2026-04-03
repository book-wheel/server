package com.bookwheel.server.community.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.dto.*;
import com.bookwheel.server.community.entity.BookInfo;
import com.bookwheel.server.community.entity.BookReview;
import com.bookwheel.server.community.entity.ReviewLike;
import com.bookwheel.server.community.repository.BookInfoRepository;
import com.bookwheel.server.community.repository.BookReviewRepository;
import com.bookwheel.server.community.repository.ReviewLikeRepository;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor

@Transactional(readOnly = true)
public class BookService {
   private final BookInfoRepository bookInfoRepository;
    private final UserRepository userRepository;
    private final BookReviewRepository bookReviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final KaKaoService kaKaoService;
    private final AladinService aladinService;


    @Transactional
    public ReviewCreateResponse createReview(ReviewCreateRequest request, String userPK) {
        String isbn = request.isbn();

        BookInfo bookInfo = bookInfoRepository.findById(isbn)
            .orElseGet(() -> bookInfoRepository.save(BookInfo.builder().isbn(isbn).build()));

        if (bookReviewRepository.existsByBookInfoAndReviewer_Id(bookInfo, userPK)) {
            throw new BusinessException(ErrorCode.ALREADY_REVIEWED);
        }


        User user = userRepository.findById(userPK)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));


        BookReview review = request.toEntity(bookInfo, user);

        BookReview savedReview = bookReviewRepository.save(review);

        return ReviewCreateResponse.from(savedReview);
    }

    @Transactional
    public void toggleReviewLike(Long reviewId, String userPK) {
        BookReview review = bookReviewRepository.findById(reviewId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        User user = userRepository.findById(userPK)
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

    public ReviewStatsResponse getReviewStats(String isbn) {

        BookInfo bookInfo = bookInfoRepository.findById(isbn).orElse(null);

        if (bookInfo == null) {
            return new ReviewStatsResponse(0, 0);
        }

        long recommendedCount = bookReviewRepository.countByBookInfoAndIsRecommended(bookInfo, true);
        long notRecommendedCount = bookReviewRepository.countByBookInfoAndIsRecommended(bookInfo, false);
        long totalCount = recommendedCount + notRecommendedCount;

        if (totalCount == 0) {
            return new ReviewStatsResponse(0, 0); // 리뷰가 없을 때
        }

        int recommendedRatio = (int) ((recommendedCount * 100) / totalCount);
        int notRecommendedRatio = 100 - recommendedRatio;

        return new ReviewStatsResponse(recommendedRatio, notRecommendedRatio);
    }

    public List<ReviewDetailResponse> getReviewList(String isbn, String userPK) {
        BookInfo bookInfo = bookInfoRepository.findById(isbn).orElse(null);

        User user = userRepository.findById(userPK)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));


        List<BookReview> reviews = bookReviewRepository.findAllByBookInfoOrderByCreatedAtDesc(bookInfo);


        return reviews.stream().map(review -> {

            boolean isLikedByMe = reviewLikeRepository.existsByReviewAndUser(review, user);

            return ReviewDetailResponse.of(review, isLikedByMe);
        }).toList();
    }


    public BookSearchListResponse searchBooks(BookSearchRequest request) {
        return kaKaoService.searchBooks(request);
    }


    public BookDetailResponse getBookDetail(String isbn) {
        return aladinService.getBookDetailByIsbn(isbn);
    }

}
