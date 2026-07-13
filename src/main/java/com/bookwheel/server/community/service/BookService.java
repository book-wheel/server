package com.bookwheel.server.community.service;

import com.bookwheel.server.common.cursor.GalleryCursor;
import com.bookwheel.server.common.cursor.InterestCursor;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.response.CursorPageResponse;
import com.bookwheel.server.common.util.CursorUtils;
import com.bookwheel.server.community.dto.*;
import com.bookwheel.server.community.entity.BookInfo;
import com.bookwheel.server.community.entity.BookLike;
import com.bookwheel.server.community.entity.BookReview;
import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.entity.ReviewLike;
import com.bookwheel.server.community.event.ReviewLikedEvent;
import com.bookwheel.server.community.repository.BookInfoRepository;
import com.bookwheel.server.community.repository.BookLikeRepository;
import com.bookwheel.server.community.repository.BookReviewRepository;
import com.bookwheel.server.community.repository.PostRepository;
import com.bookwheel.server.community.repository.ReviewLikeRepository;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.util.List;

@Service
@RequiredArgsConstructor

@Transactional(readOnly = true)
public class BookService {
   private final BookInfoRepository bookInfoRepository;
    private final UserRepository userRepository;
    private final BookReviewRepository bookReviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final BookLikeRepository bookLikeRepository;
    private final PostRepository postRepository;
    private final CursorUtils cursorUtils;
    private final KaKaoService kaKaoService;
    private final AladinService aladinService;

    private static final int DEFAULT_GALLERY_SIZE = 18;
    private static final int DEFAULT_INTEREST_SIZE = 30;


    @Transactional
    public ReviewCreateResponse createReview(ReviewCreateRequest request, String userPK) {
        String isbn = request.isbn();

        BookInfo bookInfo = bookInfoRepository.findByIsbn(isbn)
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
                    String reviewerUserPK = review.getReviewer().getId();
                    if (!reviewerUserPK.equals(userPK)) {
                        eventPublisher.publishEvent(new ReviewLikedEvent(
                                review.getReviewId(),
                                reviewerUserPK,
                                userPK,
                                user.getNickname()
                        ));
                    }
                }
            );
    }

    public ReviewStatsResponse getReviewStats(String isbn) {

        BookInfo bookInfo = bookInfoRepository.findByIsbn(isbn).orElse(null);

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
        BookInfo bookInfo = bookInfoRepository.findByIsbn(isbn).orElse(null);

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


    public BookDetailResponse getBookDetail(String isbn, String userPK) {
        boolean isInterested = bookLikeRepository.existsByBookInfo_IsbnAndUserPK(isbn, userPK);
        return aladinService.getBookDetailByIsbn(isbn, isInterested);
    }

    @Transactional
    public BookLikeResponse toggleBookLike(String isbn, String userPK) {
        BookInfo bookInfo = bookInfoRepository.findByIsbn(isbn)
            .orElseGet(() -> bookInfoRepository.save(BookInfo.builder().isbn(isbn).build()));

        if (!userRepository.existsById(userPK)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        return bookLikeRepository.findByBookInfoAndUserPK(bookInfo, userPK)
            .map(bookLike -> {
                bookLikeRepository.delete(bookLike);
                return BookLikeResponse.of(isbn, false);
            })
            .orElseGet(() -> {
                bookLikeRepository.save(BookLike.create(bookInfo, userPK));
                return BookLikeResponse.of(isbn, true);
            });
    }

    public CursorPageResponse<InterestBookResponseDto> getInterestBooks(String cursor, Integer size, String userPK) {
        if (!userRepository.existsById(userPK)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        int pageSize = resolveInterestPageSize(size);
        InterestCursor interestCursor = cursorUtils.decode(cursor, InterestCursor.class);
        validateInterestCursor(interestCursor);

        List<InterestBookResponseDto> books = findInterestBooks(userPK, interestCursor, pageSize + 1);
        boolean hasNext = books.size() > pageSize;
        List<InterestBookResponseDto> content = hasNext ? books.subList(0, pageSize) : books;

        String nextCursor = hasNext ? createNextInterestCursor(content) : null;
        Long totalElements = interestCursor == null ? bookLikeRepository.countByUserPK(userPK) : null;

        return CursorPageResponse.of(content, pageSize, totalElements, hasNext, nextCursor);
    }

    public CursorPageResponse<GalleryResponseDto> getGallery(String cursor, Integer size) {
        int pageSize = resolveGalleryPageSize(size);
        GalleryCursor galleryCursor = cursorUtils.decode(cursor, GalleryCursor.class);
        validateGalleryCursor(galleryCursor);

        List<Post> posts = postRepository.findGalleryPage(galleryCursor, pageSize + 1);
        boolean hasNext = posts.size() > pageSize;
        List<Post> pagePosts = hasNext ? posts.subList(0, pageSize) : posts;

        List<GalleryResponseDto> content = pagePosts.stream()
            .map(GalleryResponseDto::from)
            .toList();

        String nextCursor = hasNext ? createNextGalleryCursor(pagePosts) : null;
        Long totalElements = galleryCursor == null ? postRepository.countGalleryPosts() : null;

        return CursorPageResponse.of(content, pageSize, totalElements, hasNext, nextCursor);
    }

    private int resolveGalleryPageSize(Integer size) {
        if (size == null) {
            return DEFAULT_GALLERY_SIZE;
        }

        if (size <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        return size;
    }

    private int resolveInterestPageSize(Integer size) {
        if (size == null) {
            return DEFAULT_INTEREST_SIZE;
        }

        if (size <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        return size;
    }

    private void validateGalleryCursor(GalleryCursor cursor) {
        if (cursor == null) {
            return;
        }

        if (cursor.createdAt() == null || cursor.galleryId() == null) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }

    private void validateInterestCursor(InterestCursor cursor) {
        if (cursor == null) {
            return;
        }

        if (cursor.interestedAt() == null || cursor.bookId() == null) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }

    private String createNextGalleryCursor(List<Post> posts) {
        Post lastPost = posts.get(posts.size() - 1);
        return cursorUtils.encode(new GalleryCursor(lastPost.getCreatedAt(), lastPost.getPostId()));
    }

    private List<InterestBookResponseDto> findInterestBooks(String userPK, InterestCursor cursor, int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit);

        if (cursor == null) {
            return bookLikeRepository.findInterestBooksFirstPage(userPK, pageRequest);
        }

        return bookLikeRepository.findInterestBooksAfterCursor(
            userPK,
            cursor.interestedAt(),
            cursor.bookId(),
            pageRequest
        );
    }

    private String createNextInterestCursor(List<InterestBookResponseDto> books) {
        InterestBookResponseDto lastBook = books.get(books.size() - 1);
        return cursorUtils.encode(new InterestCursor(lastBook.interestedAt(), lastBook.bookId()));
    }
}
