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

import java.util.ArrayList;
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
    private final BookSearchRankingService bookSearchRankingService;
    private final AladinService aladinService;
    private final LibraryNaruService libraryNaruService;
    private final S3Service s3Service;

    private static final int DEFAULT_GALLERY_SIZE = 18;
    private static final int DEFAULT_INTEREST_SIZE = 30;
    private static final int MAX_REVIEW_PAGE_SIZE = 50;
    private static final int MAX_GALLERY_PAGE_SIZE = 50;
    private static final int MAX_INTEREST_PAGE_SIZE = 50;
    private static final int MAX_KAKAO_SEARCH_PAGE_SIZE = 50;
    // 인기순 재정렬이 적용되는 상위 구간의 크기. 이 구간을 넘어서면 카카오 검색 순서를 그대로 사용한다.
    private static final int RANKED_WINDOW_SIZE = MAX_KAKAO_SEARCH_PAGE_SIZE;


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

        if (!userRepository.existsById(userPK)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 조회 후 save 방식은 동시 요청 시 유니크 제약 위반으로 트랜잭션이 rollback-only가 될 수 있어,
        // 등록·변경을 단일 원자적 쿼리(MySQL upsert)로 처리해 경합을 DB에서 해소한다.
        bookVoteRepository.upsertVote(bookInfo.getBookInfoId(), userPK, isRecommended);

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
        List<String> reviewerPKs = reviews.stream().map(review -> review.getReviewer().getId()).distinct().toList();
        Map<String, Boolean> voteByReviewerId = reviewerPKs.isEmpty()
            ? Map.of()
            : bookVoteRepository.findByBookInfoAndUserPKs(bookInfo, reviewerPKs).stream()
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


    public BookSearchListResponse searchBooks(BookSearchRequest request, String userPK) {
        int size = Math.min(request.size(), MAX_KAKAO_SEARCH_PAGE_SIZE);
        long offset = (long) (request.page() - 1) * size;

        SearchPage searchPage = offset < RANKED_WINDOW_SIZE
            ? searchWithinRankedWindow(request, (int) offset, size)
            : searchBeyondRankedWindow(request, size);

        return new BookSearchListResponse(
            applyInterestedSearchBooks(searchPage.books(), userPK),
            searchPage.totalCount(),
            searchPage.isEnd(),
            searchPage.ranking()
        );
    }

    /*
     * 상위 RANKED_WINDOW_SIZE 건을 하나의 재정렬 구간으로 고정한다.
     * 요청 페이지와 무관하게 항상 같은 후보(카카오 1페이지)를 같은 기준으로 정렬하므로,
     * 정렬된 구간을 그대로 잘라내면 페이지 간 중복이나 누락이 발생하지 않는다.
     */
    private SearchPage searchWithinRankedWindow(BookSearchRequest request, int offset, int size) {
        BookSearchListResponse response = kaKaoService.searchBooks(
            new BookSearchRequest(request.query(), request.sort(), 1, RANKED_WINDOW_SIZE)
        );
        BookSearchRankingResult rankingResult = bookSearchRankingService.rankByPopularity(
            response.books(),
            request.query()
        );

        // 제목 일치 도서가 병합돼 후보가 늘어나도 구간 크기는 유지한다.
        // 그래야 구간 밖의 전역 순번이 카카오 검색 순번과 어긋나지 않는다.
        List<BookSearchResponse> rankedWindow = rankingResult.books().stream()
            .limit(RANKED_WINDOW_SIZE)
            .toList();
        long totalCount = resolveRankedWindowTotalCount(response, rankedWindow);

        List<BookSearchResponse> books = new ArrayList<>(sliceRankedWindow(rankedWindow, offset, size));
        // 요청 구간이 재정렬 구간 밖까지 걸쳐 있으면 나머지는 카카오 순서 그대로 이어 붙인다.
        if (books.size() < size && offset + size > RANKED_WINDOW_SIZE && !response.isEnd()) {
            books.addAll(fetchNextKakaoWindow(request, size - books.size()));
        }

        return new SearchPage(
            List.copyOf(books),
            totalCount,
            offset + books.size() >= totalCount,
            rankingResult.ranking()
        );
    }

    // 재정렬 구간을 벗어난 페이지는 카카오 검색 순서를 그대로 사용한다. (재정렬/병합 미적용)
    private SearchPage searchBeyondRankedWindow(BookSearchRequest request, int size) {
        BookSearchListResponse response = kaKaoService.searchBooks(
            new BookSearchRequest(request.query(), request.sort(), request.page(), size)
        );

        return new SearchPage(
            response.books(),
            response.totalCount(),
            response.isEnd(),
            BookSearchRankingInfo.kakao()
        );
    }

    // 재정렬 구간 바로 다음 구간(카카오 2페이지)에서 부족한 건수만큼만 가져온다.
    private List<BookSearchResponse> fetchNextKakaoWindow(BookSearchRequest request, int count) {
        return kaKaoService.searchBooks(
                new BookSearchRequest(request.query(), request.sort(), 2, MAX_KAKAO_SEARCH_PAGE_SIZE)
            )
            .books()
            .stream()
            .limit(count)
            .toList();
    }

    private List<BookSearchResponse> sliceRankedWindow(
        List<BookSearchResponse> rankedWindow,
        int offset,
        int size
    ) {
        if (offset >= rankedWindow.size()) {
            return List.of();
        }
        return rankedWindow.subList(offset, Math.min(offset + size, rankedWindow.size()));
    }

    // 카카오 결과가 재정렬 구간 안에서 끝났다면 병합된 제목 일치 도서까지가 전체 결과다.
    private long resolveRankedWindowTotalCount(
        BookSearchListResponse response,
        List<BookSearchResponse> rankedWindow
    ) {
        if (response.isEnd()) {
            return rankedWindow.size();
        }
        return Math.max(response.totalCount(), rankedWindow.size());
    }

    private record SearchPage(
        List<BookSearchResponse> books,
        long totalCount,
        boolean isEnd,
        BookSearchRankingInfo ranking
    ) {
    }

    private List<BookSearchResponse> applyInterestedSearchBooks(
        List<BookSearchResponse> books,
        String userPK
    ) {
        Set<String> interestedIsbns = findInterestedSearchIsbns(books, userPK);
        if (interestedIsbns.isEmpty()) {
            return books;
        }

        return books.stream()
            .map(book -> book.withIsInterested(interestedIsbns.contains(book.isbn())))
            .toList();
    }


    public BookDetailResponse getBookDetail(String isbn, String userPK) {
        boolean isInterested = bookLikeRepository.existsByBookInfo_IsbnAndUserPK(isbn, userPK);
        BookDetailResponse bookDetail = aladinService.getBookDetailByIsbn(isbn, isInterested);

        // 이용 분석은 부가 정보이므로 조회에 실패하면 null이 되고, 도서 상세 조회 자체는 정상 응답한다.
        return bookDetail.withUsageAnalysis(libraryNaruService.getUsageAnalysis(isbn));
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

    private Set<String> findInterestedSearchIsbns(List<BookSearchResponse> books, String userPK) {
        if (userPK == null || books.isEmpty()) {
            return Set.of();
        }

        List<String> isbns = books.stream()
            .map(BookSearchResponse::isbn)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();

        if (isbns.isEmpty()) {
            return Set.of();
        }

        return Set.copyOf(bookLikeRepository.findInterestedIsbns(userPK, isbns));
    }

    private String createNextInterestCursor(List<InterestBookResponseDto> books) {
        InterestBookResponseDto lastBook = books.get(books.size() - 1);
        return cursorUtils.encode(new InterestCursor(lastBook.interestedAt(), lastBook.bookInfoId()));
    }
}
