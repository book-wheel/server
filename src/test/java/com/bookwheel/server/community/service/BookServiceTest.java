package com.bookwheel.server.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bookwheel.server.common.cursor.GalleryCursor;
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
import com.bookwheel.server.community.entity.BookInfo;
import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.entity.PostImage;
import com.bookwheel.server.community.repository.BookInfoRepository;
import com.bookwheel.server.community.repository.BookLikeRepository;
import com.bookwheel.server.community.repository.BookReviewRepository;
import com.bookwheel.server.community.repository.PostRepository;
import com.bookwheel.server.community.repository.ReviewLikeRepository;
import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock private BookInfoRepository bookInfoRepository;
    @Mock private UserRepository userRepository;
    @Mock private BookReviewRepository bookReviewRepository;
    @Mock private ReviewLikeRepository reviewLikeRepository;
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
}
