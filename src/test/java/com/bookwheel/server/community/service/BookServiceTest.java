package com.bookwheel.server.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bookwheel.server.common.cursor.GalleryCursor;
import com.bookwheel.server.common.cursor.InterestCursor;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.response.CursorPageResponse;
import com.bookwheel.server.common.util.CursorUtils;
import com.bookwheel.server.community.dto.BookDetailResponse;
import com.bookwheel.server.community.dto.BookSearchListResponse;
import com.bookwheel.server.community.dto.BookSearchRankingResult;
import com.bookwheel.server.community.dto.BookSearchRequest;
import com.bookwheel.server.community.dto.BookSearchResponse;
import com.bookwheel.server.community.dto.BookUsageAnalysisResponse;
import com.bookwheel.server.community.dto.GalleryResponseDto;
import com.bookwheel.server.community.dto.InterestBookResponseDto;
import com.bookwheel.server.community.entity.BookInfo;
import com.bookwheel.server.community.entity.BookLike;
import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.entity.PostImage;
import com.bookwheel.server.community.event.BookLikedEvent;
import com.bookwheel.server.community.repository.BookInfoRepository;
import com.bookwheel.server.community.repository.BookLikeRepository;
import com.bookwheel.server.community.repository.BookReviewRepository;
import com.bookwheel.server.community.repository.PostRepository;
import com.bookwheel.server.community.entity.BookReview;
import com.bookwheel.server.community.repository.ReviewLikeRepository;
import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.notification.service.NotificationService;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock private BookInfoRepository bookInfoRepository;
    @Mock private UserRepository userRepository;
    @Mock private BookReviewRepository bookReviewRepository;
    @Mock private ReviewLikeRepository reviewLikeRepository;
    @Mock private NotificationService notificationService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private BookLikeRepository bookLikeRepository;
    @Mock private PostRepository postRepository;
    @Mock private CursorUtils cursorUtils;
    @Mock private KaKaoService kaKaoService;
    @Mock private BookSearchRankingService bookSearchRankingService;
    @Mock private AladinService aladinService;
    @Mock private LibraryNaruService libraryNaruService;
    @Mock private S3Service s3Service;

    @InjectMocks
    private BookService bookService;

    @Test
    @DisplayName("도서 검색 결과에 현재 사용자의 관심 도서 여부를 표시한다.")
    void searchBooks_MarksInterestedBooks() {
        String userPK = UUID.randomUUID().toString();
        BookSearchRequest request = new BookSearchRequest("clean code", null, null, null);
        BookSearchRequest expandedRequest = new BookSearchRequest("clean code", "accuracy", 1, 50);
        BookSearchResponse interestedBook = new BookSearchResponse(
                "Clean Code",
                "Robert C. Martin",
                "Prentice Hall",
                "2008-08-01",
                "https://example.com/clean-code.jpg",
                "9780132350884",
                false
        );
        BookSearchResponse otherBook = new BookSearchResponse(
                "Refactoring",
                "Martin Fowler",
                "Addison-Wesley",
                "2018-11-19",
                "https://example.com/refactoring.jpg",
                "9780134757599",
                false
        );
        BookSearchListResponse kakaoResponse = new BookSearchListResponse(
                List.of(interestedBook, otherBook),
                2,
                true
        );

        given(kaKaoService.searchBooks(expandedRequest)).willReturn(kakaoResponse);
        given(bookSearchRankingService.rankByPopularity(anyList(), eq("clean code")))
            .willAnswer(invocation -> BookSearchRankingResult.kakao(invocation.getArgument(0)));
        given(bookLikeRepository.findInterestedIsbns(
                userPK,
                List.of("9780132350884", "9780134757599")
        )).willReturn(List.of("9780132350884"));

        BookSearchListResponse response = bookService.searchBooks(request, userPK);

        assertThat(response.books()).hasSize(2);
        assertThat(response.books().get(0).isbn()).isEqualTo("9780132350884");
        assertThat(response.books().get(0).isInterested()).isTrue();
        assertThat(response.books().get(1).isbn()).isEqualTo("9780134757599");
        assertThat(response.books().get(1).isInterested()).isFalse();
        assertThat(response.ranking().source()).isEqualTo("KAKAO");
    }

    @Test
    @DisplayName("Search expands Kakao candidates before ranking and returns only requested size")
    void searchBooks_ExpandsKakaoCandidatesBeforeRanking() {
        BookSearchRequest request = new BookSearchRequest("vegetarian", null, null, null);
        BookSearchRequest expandedRequest = new BookSearchRequest("vegetarian", "accuracy", 1, 50);
        List<BookSearchResponse> kakaoCandidates = IntStream.rangeClosed(1, 50)
            .mapToObj(index -> searchBook("Kakao book " + index, "9780000000" + String.format("%03d", index)))
            .toList();
        BookSearchResponse popularBook = kakaoCandidates.get(24);
        List<BookSearchResponse> rankedBooks = Stream.concat(
                Stream.of(popularBook),
                kakaoCandidates.stream().filter(book -> !book.equals(popularBook))
            )
            .toList();

        given(kaKaoService.searchBooks(expandedRequest))
            .willReturn(new BookSearchListResponse(kakaoCandidates, 305, false));
        given(bookSearchRankingService.rankByPopularity(kakaoCandidates, "vegetarian"))
            .willReturn(BookSearchRankingResult.data4Library(
                rankedBooks,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31)
            ));

        BookSearchListResponse response = bookService.searchBooks(request, null);

        assertThat(response.books()).containsExactlyElementsOf(rankedBooks.subList(0, 10));
        assertThat(response.books().get(0)).isEqualTo(popularBook);
        assertThat(response.totalCount()).isEqualTo(305);
        assertThat(response.isEnd()).isFalse();
        assertThat(response.ranking().source()).isEqualTo("DATA4LIBRARY");
    }

    @Test
    @DisplayName("2페이지도 1페이지와 동일한 재정렬 구간을 이어서 잘라 요청한 크기만큼만 반환한다.")
    void searchBooks_SlicesSameRankedWindowOnLaterPages() {
        BookSearchRequest request = new BookSearchRequest("vegetarian", null, 2, 10);
        BookSearchRequest windowRequest = new BookSearchRequest("vegetarian", "accuracy", 1, 50);
        List<BookSearchResponse> kakaoCandidates = kakaoBooks(50);
        // 제목 일치 도서 10건이 병합돼 후보가 60건이 된 상황
        List<BookSearchResponse> rankedBooks = Stream.concat(
                naruBooks(10).stream(),
                kakaoCandidates.stream()
            )
            .toList();

        given(kaKaoService.searchBooks(windowRequest))
            .willReturn(new BookSearchListResponse(kakaoCandidates, 305, false));
        given(bookSearchRankingService.rankByPopularity(kakaoCandidates, "vegetarian"))
            .willReturn(BookSearchRankingResult.data4Library(
                rankedBooks,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31)
            ));

        BookSearchListResponse response = bookService.searchBooks(request, null);

        assertThat(response.books()).hasSize(10);
        assertThat(response.books()).containsExactlyElementsOf(rankedBooks.subList(10, 20));
        assertThat(response.totalCount()).isEqualTo(305);
        assertThat(response.isEnd()).isFalse();
        assertThat(response.ranking().source()).isEqualTo("DATA4LIBRARY");
    }

    @Test
    @DisplayName("제목 일치 도서가 병합돼도 재정렬 구간 크기를 넘겨 반환하지 않는다.")
    void searchBooks_KeepsRequestedSizeWhenTitleMatchedBooksAreMerged() {
        BookSearchRequest request = new BookSearchRequest("vegetarian", null, 1, 50);
        BookSearchRequest windowRequest = new BookSearchRequest("vegetarian", "accuracy", 1, 50);
        List<BookSearchResponse> kakaoCandidates = kakaoBooks(50);
        List<BookSearchResponse> rankedBooks = Stream.concat(
                naruBooks(10).stream(),
                kakaoCandidates.stream()
            )
            .toList();

        given(kaKaoService.searchBooks(windowRequest))
            .willReturn(new BookSearchListResponse(kakaoCandidates, 305, false));
        given(bookSearchRankingService.rankByPopularity(kakaoCandidates, "vegetarian"))
            .willReturn(BookSearchRankingResult.data4Library(
                rankedBooks,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31)
            ));

        BookSearchListResponse response = bookService.searchBooks(request, null);

        assertThat(response.books()).hasSize(50);
        assertThat(response.books()).containsExactlyElementsOf(rankedBooks.subList(0, 50));
    }

    @Test
    @DisplayName("카카오 결과가 적고 제목 일치 도서가 병합된 경우에도 isEnd를 병합 결과 기준으로 계산한다.")
    void searchBooks_CalculatesIsEndFromMergedResults() {
        BookSearchRequest windowRequest = new BookSearchRequest("vegetarian", "accuracy", 1, 50);
        List<BookSearchResponse> kakaoCandidates = kakaoBooks(2);
        // 카카오는 2건에서 끝났지만 제목 일치 도서 50건이 병합된 상황
        List<BookSearchResponse> rankedBooks = Stream.concat(
                naruBooks(50).stream(),
                kakaoCandidates.stream()
            )
            .toList();

        given(kaKaoService.searchBooks(windowRequest))
            .willReturn(new BookSearchListResponse(kakaoCandidates, 2, true));
        given(bookSearchRankingService.rankByPopularity(kakaoCandidates, "vegetarian"))
            .willReturn(BookSearchRankingResult.data4Library(
                rankedBooks,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31)
            ));

        BookSearchListResponse firstPage = bookService.searchBooks(
            new BookSearchRequest("vegetarian", null, 1, 10), null);

        // 카카오 totalCount(2)가 아니라 병합된 전체 결과를 기준으로 남은 페이지가 있음을 알린다.
        assertThat(firstPage.books()).hasSize(10);
        assertThat(firstPage.totalCount()).isEqualTo(50);
        assertThat(firstPage.isEnd()).isFalse();

        BookSearchListResponse lastPage = bookService.searchBooks(
            new BookSearchRequest("vegetarian", null, 5, 10), null);

        assertThat(lastPage.books()).containsExactlyElementsOf(rankedBooks.subList(40, 50));
        assertThat(lastPage.isEnd()).isTrue();
    }

    @Test
    @DisplayName("재정렬 구간을 벗어난 페이지는 카카오 검색 순서를 그대로 반환한다.")
    void searchBooks_UsesKakaoOrderBeyondRankedWindow() {
        BookSearchRequest request = new BookSearchRequest("vegetarian", null, 6, 10);
        BookSearchRequest kakaoRequest = new BookSearchRequest("vegetarian", "accuracy", 6, 10);
        List<BookSearchResponse> kakaoPage = kakaoBooks(10);

        given(kaKaoService.searchBooks(kakaoRequest))
            .willReturn(new BookSearchListResponse(kakaoPage, 305, false));

        BookSearchListResponse response = bookService.searchBooks(request, null);

        assertThat(response.books()).containsExactlyElementsOf(kakaoPage);
        assertThat(response.totalCount()).isEqualTo(305);
        assertThat(response.isEnd()).isFalse();
        assertThat(response.ranking().source()).isEqualTo("KAKAO");
        verify(bookSearchRankingService, never()).rankByPopularity(anyList(), any());
    }

    @Test
    @DisplayName("최신순 요청은 인기순 재정렬 없이 카카오 검색 순서를 그대로 반환한다.")
    void searchBooks_KeepsKakaoOrderWhenSortIsLatest() {
        BookSearchRequest request = new BookSearchRequest("vegetarian", "latest", 1, 10);
        List<BookSearchResponse> kakaoPage = kakaoBooks(10);

        // 후보를 확장하지 않고 요청한 페이지/크기를 그대로 카카오에 전달한다.
        given(kaKaoService.searchBooks(request))
            .willReturn(new BookSearchListResponse(kakaoPage, 305, false));

        BookSearchListResponse response = bookService.searchBooks(request, null);

        assertThat(response.books()).containsExactlyElementsOf(kakaoPage);
        assertThat(response.totalCount()).isEqualTo(305);
        assertThat(response.isEnd()).isFalse();
        assertThat(response.ranking().source()).isEqualTo("KAKAO");
        verify(bookSearchRankingService, never()).rankByPopularity(anyList(), any());
    }

    @Test
    @DisplayName("최신순 요청은 페이지가 바뀌어도 정렬 기준이 유지된다.")
    void searchBooks_KeepsKakaoOrderOnLaterPagesWhenSortIsLatest() {
        BookSearchRequest request = new BookSearchRequest("vegetarian", "latest", 2, 10);
        List<BookSearchResponse> kakaoPage = kakaoBooks(10);

        given(kaKaoService.searchBooks(request))
            .willReturn(new BookSearchListResponse(kakaoPage, 305, false));

        BookSearchListResponse response = bookService.searchBooks(request, null);

        assertThat(response.books()).containsExactlyElementsOf(kakaoPage);
        assertThat(response.ranking().source()).isEqualTo("KAKAO");
        verify(bookSearchRankingService, never()).rankByPopularity(anyList(), any());
    }

    @Test
    @DisplayName("재정렬 구간에 걸친 페이지는 남는 건수를 카카오 다음 구간에서 이어 붙인다.")
    void searchBooks_AppendsKakaoResultsWhenPageStraddlesRankedWindow() {
        BookSearchRequest request = new BookSearchRequest("vegetarian", null, 2, 30);
        BookSearchRequest windowRequest = new BookSearchRequest("vegetarian", "accuracy", 1, 50);
        BookSearchRequest nextWindowRequest = new BookSearchRequest("vegetarian", "accuracy", 2, 50);
        List<BookSearchResponse> kakaoCandidates = kakaoBooks(50);
        List<BookSearchResponse> nextKakaoWindow = naruBooks(50);

        given(kaKaoService.searchBooks(windowRequest))
            .willReturn(new BookSearchListResponse(kakaoCandidates, 305, false));
        given(kaKaoService.searchBooks(nextWindowRequest))
            .willReturn(new BookSearchListResponse(nextKakaoWindow, 305, false));
        given(bookSearchRankingService.rankByPopularity(kakaoCandidates, "vegetarian"))
            .willReturn(BookSearchRankingResult.data4Library(
                kakaoCandidates,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31)
            ));

        BookSearchListResponse response = bookService.searchBooks(request, null);

        assertThat(response.books()).hasSize(30);
        assertThat(response.books().subList(0, 20))
            .containsExactlyElementsOf(kakaoCandidates.subList(30, 50));
        assertThat(response.books().subList(20, 30))
            .containsExactlyElementsOf(nextKakaoWindow.subList(0, 10));
    }

    @Test
    @DisplayName("갤러리 size가 상한(50)을 초과하면 INVALID_INPUT_VALUE 예외를 던진다.")
    void getGallery_ThrowsWhenSizeExceedsMax() {
        assertThatThrownBy(() -> bookService.getGallery(null, 51))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    @DisplayName("특정 책 갤러리 size가 상한(50)을 초과하면 INVALID_INPUT_VALUE 예외를 던진다.")
    void getGalleryByIsbn_ThrowsWhenSizeExceedsMax() {
        assertThatThrownBy(() -> bookService.getGalleryByIsbn("9788934972464", null, 51))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    @DisplayName("관심 도서 size가 상한(50)을 초과하면 INVALID_INPUT_VALUE 예외를 던진다.")
    void getInterestBooks_ThrowsWhenSizeExceedsMax() {
        String userPK = UUID.randomUUID().toString();
        given(userRepository.existsById(userPK)).willReturn(true);

        assertThatThrownBy(() -> bookService.getInterestBooks(null, 51, userPK))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    @DisplayName("갤러리 size가 상한(50)과 같으면 예외 없이 통과한다.")
    void getGallery_AllowsSizeAtMax() {
        given(cursorUtils.decode(null, GalleryCursor.class)).willReturn(null);
        given(postRepository.findGalleryPage(null, 51)).willReturn(List.of());
        given(postRepository.countGalleryPosts()).willReturn(0L);

        // size=50 이면 상한 이내이므로 예외가 발생하지 않아야 한다.
        assertThat(bookService.getGallery(null, 50)).isNotNull();
    }

    @Test
    @DisplayName("갤러리 thumbnailUrl은 objectKey가 아닌 Presigned URL로 변환되어 반환된다.")
    void getGallery_ConvertsThumbnailToPresignedUrl() {
        String objectKey = "posts/1/thumbnail.jpg";
        String presignedUrl = "https://bucket.s3.amazonaws.com/posts/1/thumbnail.jpg?X-Amz-Signature=abc";

        PostImage image = mock(PostImage.class);
        given(image.getObjectKey()).willReturn(objectKey);

        BookInfo bookInfo = mock(BookInfo.class);
        given(bookInfo.getIsbn()).willReturn("9788934972464");

        Post post = mock(Post.class);
        given(post.getImages()).willReturn(List.of(image));
        given(post.getBookInfo()).willReturn(bookInfo);
        given(post.getPostId()).willReturn(10L);
        given(post.getCreatedAt()).willReturn(LocalDateTime.of(2026, 7, 14, 0, 0));

        given(cursorUtils.decode(null, GalleryCursor.class)).willReturn(null);
        given(postRepository.findGalleryPage(null, 19)).willReturn(List.of(post));
        given(postRepository.countGalleryPosts()).willReturn(1L);
        given(s3Service.getPresignedGetUrl(objectKey)).willReturn(presignedUrl);

        CursorPageResponse<GalleryResponseDto> response = bookService.getGallery(null, null);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).thumbnailUrl()).isEqualTo(presignedUrl);
    }

    @Test
    @DisplayName("관심 도서가 없으면 빈 목록과 다음 페이지 없음을 반환한다.")
    void getInterestBooks_ReturnsEmptyPageWhenNoInterestBooks() {
        String userPK = UUID.randomUUID().toString();
        given(userRepository.existsById(userPK)).willReturn(true);
        given(cursorUtils.decode(null, InterestCursor.class)).willReturn(null);
        given(bookLikeRepository.findInterestBooks(eq(userPK), eq(null), eq(null), any())).willReturn(List.of());
        given(bookLikeRepository.countByUserPK(userPK)).willReturn(0L);

        CursorPageResponse<InterestBookResponseDto> response = bookService.getInterestBooks(null, null, userPK);

        assertThat(response.content()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        assertThat(response.totalElements()).isZero();
    }

    @Test
    @DisplayName("관심 도서 목록은 등록 최신순으로 반환되며 요청한 size 만큼만 내려간다.")
    void getInterestBooks_ReturnsRecentlyInterestedFirstWithinRequestedSize() {
        String userPK = UUID.randomUUID().toString();
        InterestBookResponseDto recent = interestBook(2L, "9788934972464", LocalDateTime.of(2026, 7, 14, 12, 0));
        InterestBookResponseDto older = interestBook(1L, "9791165341909", LocalDateTime.of(2026, 7, 13, 12, 0));

        given(userRepository.existsById(userPK)).willReturn(true);
        given(cursorUtils.decode(null, InterestCursor.class)).willReturn(null);
        // size + 1 건을 조회해 다음 페이지 존재 여부를 판단한다.
        given(bookLikeRepository.findInterestBooks(eq(userPK), eq(null), eq(null), eq(PageRequest.of(0, 2))))
            .willReturn(List.of(recent, older));
        given(cursorUtils.encode(any(InterestCursor.class))).willReturn("next-cursor");

        CursorPageResponse<InterestBookResponseDto> response = bookService.getInterestBooks(null, 1, userPK);

        assertThat(response.content()).containsExactly(recent);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo("next-cursor");
        verify(cursorUtils).encode(new InterestCursor(recent.interestedAt(), recent.bookInfoId()));
    }

    @Test
    @DisplayName("커서가 있으면 커서 이후의 관심 도서를 조회한다.")
    void getInterestBooks_UsesCursorQueryWhenCursorGiven() {
        String userPK = UUID.randomUUID().toString();
        InterestCursor cursor = new InterestCursor(LocalDateTime.of(2026, 7, 14, 12, 0), 5L);

        given(userRepository.existsById(userPK)).willReturn(true);
        given(cursorUtils.decode("encoded-cursor", InterestCursor.class)).willReturn(cursor);
        given(bookLikeRepository.findInterestBooks(
            eq(userPK), eq(cursor.interestedAt()), eq(cursor.bookId()), any()
        )).willReturn(List.of());

        CursorPageResponse<InterestBookResponseDto> response =
            bookService.getInterestBooks("encoded-cursor", null, userPK);

        assertThat(response.content()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        // 다음 페이지 조회에서는 전체 개수를 다시 세지 않는다.
        assertThat(response.totalElements()).isNull();
        verify(bookLikeRepository, never()).countByUserPK(any());
    }

    @Test
    @DisplayName("관심 도서로 등록하면 도서 정보를 채우는 이벤트를 발행하고, 트랜잭션 안에서는 외부 API를 호출하지 않는다.")
    void toggleBookLike_PublishesBookLikedEventOnInterest() {
        String isbn = "9788954681179";
        String userPK = UUID.randomUUID().toString();
        BookInfo bookInfo = BookInfo.builder().isbn(isbn).build();

        given(bookInfoRepository.findOrCreateByIsbn(isbn)).willReturn(bookInfo);
        given(userRepository.existsById(userPK)).willReturn(true);
        given(bookLikeRepository.findByBookInfoAndUserPK(bookInfo, userPK)).willReturn(Optional.empty());

        assertThat(bookService.toggleBookLike(isbn, userPK).liked()).isTrue();

        then(eventPublisher).should().publishEvent(new BookLikedEvent(isbn));
        then(aladinService).should(never()).getBookDetailByIsbn(any(), anyBoolean());
    }

    @Test
    @DisplayName("도서 정보를 이미 저장한 도서는 찜해도 조회 이벤트를 발행하지 않는다.")
    void toggleBookLike_SkipsEventWhenBookDetailsAlreadyStored() {
        String isbn = "9788954681179";
        String userPK = UUID.randomUUID().toString();
        BookInfo bookInfo = BookInfo.builder()
            .isbn(isbn)
            .title("밝은 밤")
            .author("최은영")
            .coverImage("https://image.aladin.co.kr/cover.jpg")
            .build();

        given(bookInfoRepository.findOrCreateByIsbn(isbn)).willReturn(bookInfo);
        given(userRepository.existsById(userPK)).willReturn(true);
        given(bookLikeRepository.findByBookInfoAndUserPK(bookInfo, userPK)).willReturn(Optional.empty());

        bookService.toggleBookLike(isbn, userPK);

        then(eventPublisher).should(never()).publishEvent(any(BookLikedEvent.class));
    }

    @Test
    @DisplayName("관심 도서를 취소할 때는 도서 정보 조회 이벤트를 발행하지 않는다.")
    void toggleBookLike_SkipsEventWhenCancelingInterest() {
        String isbn = "9788954681179";
        String userPK = UUID.randomUUID().toString();
        BookInfo bookInfo = BookInfo.builder().isbn(isbn).build();

        given(bookInfoRepository.findOrCreateByIsbn(isbn)).willReturn(bookInfo);
        given(userRepository.existsById(userPK)).willReturn(true);
        given(bookLikeRepository.findByBookInfoAndUserPK(bookInfo, userPK))
            .willReturn(Optional.of(BookLike.create(bookInfo, userPK)));

        assertThat(bookService.toggleBookLike(isbn, userPK).liked()).isFalse();
        then(eventPublisher).should(never()).publishEvent(any(BookLikedEvent.class));
    }

    private InterestBookResponseDto interestBook(Long bookInfoId, String isbn, LocalDateTime interestedAt) {
        return new InterestBookResponseDto(
                bookInfoId,
                isbn,
                "밝은 밤",
                "최은영",
                "https://image.aladin.co.kr/cover.jpg",
                interestedAt
        );
    }

    private BookDetailResponse sampleBookDetail(String isbn) {
        return new BookDetailResponse(
                "밝은 밤",
                "최은영",
                "문학동네",
                "책 소개",
                "https://image.aladin.co.kr/cover.jpg",
                340,
                isbn,
                true,
                null
        );
    }

    private List<BookSearchResponse> kakaoBooks(int count) {
        return IntStream.rangeClosed(1, count)
            .mapToObj(index -> searchBook("Kakao book " + index, "9780000000" + String.format("%03d", index)))
            .toList();
    }

    private List<BookSearchResponse> naruBooks(int count) {
        return IntStream.rangeClosed(1, count)
            .mapToObj(index -> searchBook("Naru book " + index, "9791111111" + String.format("%03d", index)))
            .toList();
    }

    private BookSearchResponse searchBook(String title, String isbn) {
        return new BookSearchResponse(
            title,
            "author",
            "publisher",
            "2026-01-01",
            "https://example.com/book.jpg",
            isbn,
            false
        );
    }

    private BookUsageAnalysisResponse sampleUsageAnalysis() {
        return new BookUsageAnalysisResponse(104490, "40대", List.of("최은영"));
    }

    @Test
    @DisplayName("도서 상세 조회 응답에 도서관정보나루 이용 분석 정보가 함께 담긴다.")
    void getBookDetail_IncludesUsageAnalysis() {
        String isbn = "9788954681179";
        String userPK = UUID.randomUUID().toString();

        given(bookLikeRepository.existsByBookInfo_IsbnAndUserPK(isbn, userPK)).willReturn(true);
        given(aladinService.getBookDetailByIsbn(isbn, true)).willReturn(sampleBookDetail(isbn));
        given(libraryNaruService.getUsageAnalysis(isbn)).willReturn(sampleUsageAnalysis());

        BookDetailResponse response = bookService.getBookDetail(isbn, userPK);

        BookUsageAnalysisResponse usageAnalysis = response.usageAnalysis();
        assertThat(usageAnalysis).isNotNull();
        assertThat(usageAnalysis.totalLoanCount()).isEqualTo(104490);
        assertThat(usageAnalysis.mostLoanedAgeGroup()).isEqualTo("40대");
        assertThat(usageAnalysis.keywords()).containsExactly("최은영");
    }

    @Test
    @DisplayName("도서관정보나루 조회에 실패해도 도서 상세 조회는 성공하고 usageAnalysis만 null이 된다.")
    void getBookDetail_SucceedsWhenUsageAnalysisUnavailable() {
        String isbn = "9788954681179";
        String userPK = UUID.randomUUID().toString();

        given(bookLikeRepository.existsByBookInfo_IsbnAndUserPK(isbn, userPK)).willReturn(true);
        given(aladinService.getBookDetailByIsbn(isbn, true)).willReturn(sampleBookDetail(isbn));
        // 외부 API 호출 실패, 타임아웃, 데이터 없음은 모두 null로 넘어온다.
        given(libraryNaruService.getUsageAnalysis(isbn)).willReturn(null);

        BookDetailResponse response = bookService.getBookDetail(isbn, userPK);

        assertThat(response.usageAnalysis()).isNull();
        // 기존 도서 상세 필드는 그대로 유지되어야 한다.
        assertThat(response.title()).isEqualTo("밝은 밤");
        assertThat(response.author()).isEqualTo("최은영");
        assertThat(response.isbn()).isEqualTo(isbn);
        assertThat(response.itemPage()).isEqualTo(340);
        assertThat(response.isInterested()).isTrue();
    }

    @Test
    @DisplayName("리뷰 삭제는 작성자 본인이면 알림과 공감을 먼저 정리한 뒤 리뷰를 삭제한다")
    void deleteReview_RemovesRelatedRowsBeforeReview() {
        Long reviewId = 5L;
        String userPK = UUID.randomUUID().toString();
        BookReview review = mock(BookReview.class);
        User reviewer = mock(User.class);

        given(bookReviewRepository.findByReviewIdForUpdate(reviewId)).willReturn(Optional.of(review));
        given(review.getReviewer()).willReturn(reviewer);
        given(reviewer.getId()).willReturn(userPK);

        bookService.deleteReview(reviewId, userPK);

        InOrder inOrder = inOrder(notificationService, reviewLikeRepository, bookReviewRepository);
        inOrder.verify(notificationService).deleteByReviewId(reviewId);
        inOrder.verify(reviewLikeRepository).deleteAllByReview(review);
        inOrder.verify(bookReviewRepository).delete(review);
    }

    @Test
    @DisplayName("리뷰 삭제는 작성자 본인이 아니면 예외가 발생하고 아무것도 정리하지 않는다")
    void deleteReview_ThrowsWhenNotOwner() {
        Long reviewId = 5L;
        String userPK = UUID.randomUUID().toString();
        BookReview review = mock(BookReview.class);
        User reviewer = mock(User.class);

        given(bookReviewRepository.findByReviewIdForUpdate(reviewId)).willReturn(Optional.of(review));
        given(review.getReviewer()).willReturn(reviewer);
        given(reviewer.getId()).willReturn(UUID.randomUUID().toString());

        assertThatThrownBy(() -> bookService.deleteReview(reviewId, userPK))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.REVIEW_DELETE_FORBIDDEN));

        then(notificationService).shouldHaveNoInteractions();
        then(reviewLikeRepository).shouldHaveNoInteractions();
        then(bookReviewRepository).should(never()).delete(any(BookReview.class));
    }
}
