package com.bookwheel.server.community.service;

import com.bookwheel.server.common.cursor.GalleryCursor;
import com.bookwheel.server.common.cursor.InterestCursor;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.response.CursorPageResponse;
import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.common.util.CursorUtils;
import com.bookwheel.server.community.dto.*;
import com.bookwheel.server.community.entity.BookInfo;
import com.bookwheel.server.community.entity.BookLike;
import com.bookwheel.server.community.entity.BookReview;
import com.bookwheel.server.community.entity.BookVote;
import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.entity.ReviewLike;
import com.bookwheel.server.community.event.ReviewLikedEvent;
import com.bookwheel.server.community.repository.BookInfoRepository;
import com.bookwheel.server.community.repository.BookLikeRepository;
import com.bookwheel.server.community.repository.BookReviewRepository;
import com.bookwheel.server.community.repository.BookVoteRepository;
import com.bookwheel.server.community.repository.PostRepository;
import com.bookwheel.server.community.repository.ReviewLikeRepository;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor

@Transactional(readOnly = true)
public class BookService {
   private final BookInfoRepository bookInfoRepository;
    private final UserRepository userRepository;
    private final BookReviewRepository bookReviewRepository;
    private final BookVoteRepository bookVoteRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final BookLikeRepository bookLikeRepository;
    private final PostRepository postRepository;
    private final CursorUtils cursorUtils;
    private final KaKaoService kaKaoService;
    private final AladinService aladinService;
    private final S3Service s3Service;

    private static final int DEFAULT_GALLERY_SIZE = 18;
    private static final int DEFAULT_INTEREST_SIZE = 30;
    private static final int MAX_REVIEW_PAGE_SIZE = 50;
    private static final int MAX_GALLERY_PAGE_SIZE = 50;
    private static final int MAX_INTEREST_PAGE_SIZE = 50;


    @Transactional
    public ReviewDetailResponse createReview(String isbn, ReviewCreateRequest request, String userPK) {
        BookInfo bookInfo = bookInfoRepository.findByIsbn(isbn)
            .orElseGet(() -> bookInfoRepository.save(BookInfo.builder().isbn(isbn).build()));

        if (bookReviewRepository.existsByBookInfoAndReviewer_Id(bookInfo, userPK)) {
            throw new BusinessException(ErrorCode.ALREADY_REVIEWED);
        }


        User user = userRepository.findById(userPK)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));


        BookReview review = request.toEntity(bookInfo, user);

        BookReview savedReview;
        try {
            savedReview = bookReviewRepository.save(review);
        } catch (DataIntegrityViolationException e) {
            // exists 검사와 save 사이 동시 요청으로 (book_info_id, user_id) 유니크 제약을 위반한 경우
            throw new BusinessException(ErrorCode.ALREADY_REVIEWED);
        }

        // 추천/비추천은 별도 투표에서 파생 (미투표 시 null), 방금 작성한 리뷰이므로 공감 수 0·내 공감 여부 false
        Boolean isRecommended = bookVoteRepository.findByBookInfoAndUser_Id(bookInfo, userPK)
            .map(BookVote::getIsRecommended)
            .orElse(null);
        String profileImageUrl = getProfileImageUrl(user.getProfileImageKey());
        return ReviewDetailResponse.of(savedReview, profileImageUrl, false, isRecommended);
    }

    @Transactional
    public ReviewLikeResponse toggleReviewLike(Long reviewId, String userPK) {
        BookReview review = bookReviewRepository.findById(reviewId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        User user = userRepository.findById(userPK)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        boolean isLikedByMe = reviewLikeRepository.findByReviewAndUser(review, user)
            .map(reviewLike -> {
                // 이미 하트를 눌렀던 상태면 -> 좋아요 취소
                reviewLikeRepository.delete(reviewLike);
                review.decreaseLikeCount();
                return false;
            })
            .orElseGet(() -> {
                // 하트를 누르지 않은 상태면 -> 좋아요 추가
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
                return true;
            });

        return ReviewLikeResponse.of(review.getReviewId(), isLikedByMe, review.getLikeCount());
    }

    // 리뷰(코멘트) 삭제. 작성자 본인만 삭제할 수 있으며, 연결된 공감(하트)을 먼저 제거한 뒤 리뷰를 삭제한다.
    @Transactional
    public void deleteReview(Long reviewId, String userPK) {
        BookReview review = bookReviewRepository.findById(reviewId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        if (!review.getReviewer().getId().equals(userPK)) {
            throw new BusinessException(ErrorCode.REVIEW_DELETE_FORBIDDEN);
        }

        reviewLikeRepository.deleteByReview(review);
        bookReviewRepository.delete(review);
    }

    // 추천/비추천 등록·변경. 기존 투표가 없으면 등록, 있으면 값 변경(같은 값이면 그대로 유지)한다.
    @Transactional
    public ReviewVoteResponse upsertVote(String isbn, boolean isRecommended, String userPK) {
        BookInfo bookInfo = bookInfoRepository.findByIsbn(isbn)
            .orElseGet(() -> bookInfoRepository.save(BookInfo.builder().isbn(isbn).build()));

        User user = userRepository.findById(userPK)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        bookVoteRepository.findByBookInfoAndUser_Id(bookInfo, userPK)
            .ifPresentOrElse(
                vote -> vote.changeVote(isRecommended),
                () -> {
                    try {
                        bookVoteRepository.save(BookVote.create(bookInfo, user, isRecommended));
                    } catch (DataIntegrityViolationException e) {
                        // 조회와 save 사이 동시 요청으로 (book_info_id, user_id) 유니크 제약을 위반한 경우 -> 기존 투표 변경
                        bookVoteRepository.findByBookInfoAndUser_Id(bookInfo, userPK)
                            .ifPresent(vote -> vote.changeVote(isRecommended));
                    }
                }
            );

        return buildVoteResponse(bookInfo, userPK);
    }

    // 추천/비추천 취소. 투표가 없어도 오류 없이 성공 처리한다.
    @Transactional
    public ReviewVoteResponse cancelVote(String isbn, String userPK) {
        if (!userRepository.existsById(userPK)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        BookInfo bookInfo = bookInfoRepository.findByIsbn(isbn).orElse(null);
        if (bookInfo == null) {
            return new ReviewVoteResponse(isbn, 0, 0, null);
        }

        bookVoteRepository.findByBookInfoAndUser_Id(bookInfo, userPK)
            .ifPresent(bookVoteRepository::delete);

        return buildVoteResponse(bookInfo, userPK);
    }

    public ReviewStatsResponse getReviewStats(String isbn, String userPK) {
        BookInfo bookInfo = bookInfoRepository.findByIsbn(isbn).orElse(null);

        if (bookInfo == null) {
            return new ReviewStatsResponse(0, 0, null);
        }

        ReviewVoteResponse stats = buildVoteResponse(bookInfo, userPK);
        return new ReviewStatsResponse(stats.recommendedRatio(), stats.notRecommendedRatio(), stats.myVote());
    }

    // 해당 도서의 추천/비추천 비율과 로그인 사용자의 선택값(myVote)을 계산한다.
    private ReviewVoteResponse buildVoteResponse(BookInfo bookInfo, String userPK) {
        VoteType myVote = resolveMyVote(bookInfo, userPK);

        long recommendedCount = bookVoteRepository.countByBookInfoAndIsRecommended(bookInfo, true);
        long notRecommendedCount = bookVoteRepository.countByBookInfoAndIsRecommended(bookInfo, false);
        long totalCount = recommendedCount + notRecommendedCount;

        if (totalCount == 0) {
            return new ReviewVoteResponse(bookInfo.getIsbn(), 0, 0, myVote); // 투표가 없을 때
        }

        int recommendedRatio = (int) ((recommendedCount * 100) / totalCount);
        int notRecommendedRatio = 100 - recommendedRatio;

        return new ReviewVoteResponse(bookInfo.getIsbn(), recommendedRatio, notRecommendedRatio, myVote);
    }

    // 로그인 사용자가 해당 도서에 투표한 추천 여부를 myVote로 변환한다. (비로그인/미투표 시 null)
    private VoteType resolveMyVote(BookInfo bookInfo, String userPK) {
        if (userPK == null) {
            return null;
        }
        return bookVoteRepository.findByBookInfoAndUser_Id(bookInfo, userPK)
            .map(vote -> VoteType.fromRecommended(vote.getIsRecommended()))
            .orElse(null);
    }

    public Page<ReviewDetailResponse> getReviewList(String isbn, String sort, int page, int size, String userPK) {
        if (page < 0 || size <= 0 || size > MAX_REVIEW_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        User user = userRepository.findById(userPK)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Pageable pageable = PageRequest.of(page, size, resolveReviewSort(sort));

        BookInfo bookInfo = bookInfoRepository.findByIsbn(isbn).orElse(null);
        if (bookInfo == null) {
            return Page.empty(pageable);
        }

        Page<BookReview> reviews = bookReviewRepository.findAllByBookInfo(bookInfo, pageable);

        // 현재 페이지 리뷰들에 대한 내 공감 여부를 한 번의 쿼리로 조회 (리뷰별 exists N+1 방지)
        List<Long> reviewIds = reviews.stream().map(BookReview::getReviewId).toList();
        Set<Long> likedReviewIds = reviewIds.isEmpty()
            ? Set.of()
            : Set.copyOf(reviewLikeRepository.findLikedReviewIds(user, reviewIds));

        // 현재 페이지 리뷰 작성자들의 추천/비추천 투표를 한 번의 쿼리로 조회 (리뷰별 조회 N+1 방지)
        List<String> reviewerIds = reviews.stream().map(review -> review.getReviewer().getId()).distinct().toList();
        Map<String, Boolean> voteByReviewerId = reviewerIds.isEmpty()
            ? Map.of()
            : bookVoteRepository.findByBookInfoAndUserIds(bookInfo, reviewerIds).stream()
                .collect(Collectors.toMap(vote -> vote.getUser().getId(), BookVote::getIsRecommended));

        return reviews.map(review -> {
            boolean isLikedByMe = likedReviewIds.contains(review.getReviewId());
            String profileImageUrl = getProfileImageUrl(review.getReviewer().getProfileImageKey());
            Boolean isRecommended = voteByReviewerId.get(review.getReviewer().getId());

            return ReviewDetailResponse.of(review, profileImageUrl, isLikedByMe, isRecommended);
        });
    }

    // 정렬 기준을 Sort로 변환한다. popular=공감수 내림차순(동일 시 작성일 내림차순), 그 외 latest=작성일 내림차순
    private Sort resolveReviewSort(String sort) {
        if ("popular".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Order.desc("likeCount"), Sort.Order.desc("createdAt"));
        }
        return Sort.by(Sort.Order.desc("createdAt"));
    }

    // 프로필 이미지 키를 조회용 Presigned URL로 변환한다. (키가 없으면 null)
    private String getProfileImageUrl(String profileImageKey) {
        if (!StringUtils.hasText(profileImageKey)) {
            return null;
        }
        return s3Service.getPresignedGetUrl(profileImageKey);
    }

    // 갤러리 대표 이미지 objectKey를 조회용 Presigned URL로 변환한다. (이미지가 없으면 null)
    private GalleryResponseDto toGalleryResponse(Post post) {
        String thumbnailObjectKey = GalleryResponseDto.thumbnailObjectKey(post);
        String thumbnailUrl = StringUtils.hasText(thumbnailObjectKey)
            ? s3Service.getPresignedGetUrl(thumbnailObjectKey)
            : null;
        return GalleryResponseDto.from(post, thumbnailUrl);
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
            .map(this::toGalleryResponse)
            .toList();

        String nextCursor = hasNext ? createNextGalleryCursor(pagePosts) : null;
        Long totalElements = galleryCursor == null ? postRepository.countGalleryPosts() : null;

        return CursorPageResponse.of(content, pageSize, totalElements, hasNext, nextCursor);
    }

    public CursorPageResponse<GalleryResponseDto> getGalleryByIsbn(String isbn, String cursor, Integer size) {
        int pageSize = resolveGalleryPageSize(size);
        GalleryCursor galleryCursor = cursorUtils.decode(cursor, GalleryCursor.class);
        validateGalleryCursor(galleryCursor);

        List<Post> posts = postRepository.findGalleryPageByIsbn(isbn, galleryCursor, pageSize + 1);
        boolean hasNext = posts.size() > pageSize;
        List<Post> pagePosts = hasNext ? posts.subList(0, pageSize) : posts;

        List<GalleryResponseDto> content = pagePosts.stream()
            .map(this::toGalleryResponse)
            .toList();

        String nextCursor = hasNext ? createNextGalleryCursor(pagePosts) : null;
        Long totalElements = galleryCursor == null ? postRepository.countGalleryPostsByIsbn(isbn) : null;

        return CursorPageResponse.of(content, pageSize, totalElements, hasNext, nextCursor);
    }

    private int resolveGalleryPageSize(Integer size) {
        if (size == null) {
            return DEFAULT_GALLERY_SIZE;
        }

        if (size <= 0 || size > MAX_GALLERY_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        return size;
    }

    private int resolveInterestPageSize(Integer size) {
        if (size == null) {
            return DEFAULT_INTEREST_SIZE;
        }

        if (size <= 0 || size > MAX_INTEREST_PAGE_SIZE) {
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
        return cursorUtils.encode(new InterestCursor(lastBook.interestedAt(), lastBook.bookInfoId()));
    }
}
