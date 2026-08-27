package com.bookwheel.server.community.controller;

import com.bookwheel.server.common.response.CursorPageResponse;
import com.bookwheel.server.community.dto.BookDetailResponse;
import com.bookwheel.server.community.dto.BookExchangeRecommendationBasis;
import com.bookwheel.server.community.dto.BookExchangeRecommendationBook;
import com.bookwheel.server.community.dto.BookExchangeRecommendationResponse;
import com.bookwheel.server.community.dto.BookExchangeRecommendationReview;
import com.bookwheel.server.community.dto.BookSearchListResponse;
import com.bookwheel.server.community.dto.BookSearchResponse;
import com.bookwheel.server.community.dto.BookUsageAnalysisResponse;
import com.bookwheel.server.community.dto.CurrentReadingBookResponse;
import com.bookwheel.server.community.dto.CurrentReadingBooksResponse;
import com.bookwheel.server.community.dto.GalleryResponseDto;
import com.bookwheel.server.community.dto.InterestBookResponseDto;
import com.bookwheel.server.community.dto.ReviewDetailResponse;
import com.bookwheel.server.community.dto.ReviewLikeResponse;
import com.bookwheel.server.community.dto.ReviewStatsResponse;
import com.bookwheel.server.community.dto.VoteType;
import com.bookwheel.server.community.service.BookExchangeRecommendationService;
import com.bookwheel.server.community.service.BookService;
import com.bookwheel.server.community.service.CurrentReadingBookService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookController.class)
class BookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookService bookService;

    @MockitoBean
    private BookExchangeRecommendationService bookExchangeRecommendationService;

    @MockitoBean
    private CurrentReadingBookService currentReadingBookService;

    @RegisterExtension
    TestWatcher watcher = new TestWatcher() {
        @Override
        public void testSuccessful(ExtensionContext context) {
            System.out.println("SUCCESS: " + context.getDisplayName());
        }

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            System.out.println("FAIL: " + context.getDisplayName());
            System.out.println("SUCCESS: " + cause.getMessage());
        }
    };

    private ReviewDetailResponse sampleReview(String isbn) {
        return new ReviewDetailResponse(
                1L,
                isbn,
                "reviewer",
                "https://cdn.example.com/profile.png",
                true,
                "Great read",
                false,
                3,
                true,
                LocalDateTime.of(2024, 1, 1, 10, 0)
        );
    }

    private BookDetailResponse sampleBookDetail(String isbn, BookUsageAnalysisResponse usageAnalysis) {
        return new BookDetailResponse(
                "밝은 밤",
                "최은영",
                "문학동네",
                "책 소개",
                "https://image.aladin.co.kr/cover.jpg",
                340,
                isbn,
                true,
                usageAnalysis
        );
    }

    @Test
    @WithMockUser
    @DisplayName("도서 상세 조회 응답에 이용 분석 정보가 포함된다.")
    void getBookDetail_WithUsageAnalysis() throws Exception {
        String isbn = "9788954681179";
        BookUsageAnalysisResponse usageAnalysis =
                new BookUsageAnalysisResponse(10879, "40대", List.of("역사", "소설", "광주민주화운동"));
        given(bookService.getBookDetail(eq(isbn), any())).willReturn(sampleBookDetail(isbn, usageAnalysis));

        mockMvc.perform(get("/api/v1/books/{isbn}", isbn))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.usageAnalysis.totalLoanCount").value(10879))
                .andExpect(jsonPath("$.data.usageAnalysis.mostLoanedAgeGroup").value("40대"))
                .andExpect(jsonPath("$.data.usageAnalysis.keywords.length()").value(3))
                .andExpect(jsonPath("$.data.usageAnalysis.keywords[0]").value("역사"))
                .andExpect(jsonPath("$.data.usageAnalysis.keywords[2]").value("광주민주화운동"))
                // 목차 영역은 이용 분석으로 대체되었으므로 toc 필드는 더 이상 내려가지 않는다.
                .andExpect(jsonPath("$.data.toc").doesNotExist());
    }

    @Test
    @WithMockUser
    @DisplayName("이용 분석 정보가 없어도 도서 상세 조회는 성공하고 usageAnalysis는 null로 내려간다.")
    void getBookDetail_WithoutUsageAnalysis() throws Exception {
        String isbn = "9788954681179";
        given(bookService.getBookDetail(eq(isbn), any())).willReturn(sampleBookDetail(isbn, null));

        mockMvc.perform(get("/api/v1/books/{isbn}", isbn))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                // 기존 도서 상세 필드는 그대로 유지된다.
                .andExpect(jsonPath("$.data.title").value("밝은 밤"))
                .andExpect(jsonPath("$.data.isbn").value(isbn))
                // 필드가 생략되지 않고 null로 내려가야 프론트에서 데이터 없음을 구분할 수 있다.
                .andExpect(content().string(containsString("\"usageAnalysis\":null")));
    }

    @Test
    @WithMockUser(username = "user-pk")
    @DisplayName("Book Exchange Recommendation: returns daily recommendation with source metadata")
    void getExchangeRecommendation_Success() throws Exception {
        BookExchangeRecommendationResponse response = new BookExchangeRecommendationResponse(
            LocalDate.of(2026, 8, 8),
            new BookExchangeRecommendationBasis(
                "DAILY_ROTATION",
                "DATA4LIBRARY",
                "도서관 정보나루",
                "국립중앙도서관",
                "https://www.data4library.kr/apiUtilization",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                "전월 인기대출도서 순위 기반 일별 추천"
            ),
            new BookExchangeRecommendationBook(
                "9788954681179",
                "밝은 밤",
                "최은영",
                "https://example.com/cover.jpg",
                8,
                104490,
                12,
                true,
                new BookExchangeRecommendationReview(
                    1L,
                    "문희연",
                    "좋은 후기",
                    7,
                    LocalDateTime.of(2026, 8, 1, 10, 0)
                )
            )
        );
        given(bookExchangeRecommendationService.getDailyRecommendation("user-pk")).willReturn(response);

        mockMvc.perform(get("/api/v1/books/exchange-recommendation"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.recommendationDate").value("2026-08-08"))
            .andExpect(jsonPath("$.data.basis.source").value("DATA4LIBRARY"))
            .andExpect(jsonPath("$.data.basis.sourceName").value("도서관 정보나루"))
            .andExpect(jsonPath("$.data.basis.provider").value("국립중앙도서관"))
            .andExpect(jsonPath("$.data.basis.startDate").value("2026-07-01"))
            .andExpect(jsonPath("$.data.basis.endDate").value("2026-07-31"))
            .andExpect(jsonPath("$.data.book.isbn").value("9788954681179"))
            .andExpect(jsonPath("$.data.book.likeCount").value(12))
            .andExpect(jsonPath("$.data.book.isInterested").value(true))
            .andExpect(jsonPath("$.data.book.review.reviewerName").value("문희연"))
            .andExpect(jsonPath("$.data.book.review.comment").value("좋은 후기"));
    }

    @Test
    @WithMockUser(username = "user-pk")
    @DisplayName("Book Search: returns interest state for each book")
    void searchBooks_IncludesInterestedState() throws Exception {
        BookSearchListResponse response = new BookSearchListResponse(
                List.of(new BookSearchResponse(
                        "Clean Code",
                        "Robert C. Martin",
                        "Prentice Hall",
                        "2008-08-01",
                        "https://example.com/clean-code.jpg",
                        "9780132350884",
                        true
                )),
                1,
                true
        );
        given(bookService.searchBooks(any(), eq("user-pk"))).willReturn(response);

        mockMvc.perform(get("/api/v1/books/search")
                        .param("query", "clean code"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.books[0].isbn").value("9780132350884"))
                .andExpect(jsonPath("$.data.books[0].isInterested").value(true))
                .andExpect(jsonPath("$.data.ranking.source").value("KAKAO"))
                .andExpect(jsonPath("$.data.ranking.sourceName").value("카카오 도서 검색 API"));
    }

    @Test
    @WithMockUser(username = "user-pk")
    @DisplayName("시작 예정 책 조회 응답에는 상태와 라운드 시작일, D-day가 포함된다")
    void getCurrentReadingBooks_ReturnsUpcomingBookStatusAndDday() throws Exception {
        CurrentReadingBooksResponse response = new CurrentReadingBooksResponse(List.of(
            new CurrentReadingBookResponse(
                    "group-123",
                    "달러구트 꿈 백화점",
                    "https://image.aladin.co.kr/cover.jpg",
                    true,
                    LocalDate.of(2026, 8, 21),
                    2
            )
        ));
        given(currentReadingBookService.getCurrentReadingBooks("user-pk")).willReturn(response);

        mockMvc.perform(get("/api/v1/books/current-reading"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.books[0].groupId").value("group-123"))
            .andExpect(jsonPath("$.data.books[0].title").value("달러구트 꿈 백화점"))
            .andExpect(jsonPath("$.data.books[0].coverImageUrl").value("https://image.aladin.co.kr/cover.jpg"))
            .andExpect(jsonPath("$.data.books[0].upcoming").value(true))
            .andExpect(jsonPath("$.data.books[0].roundStartDate").value("2026-08-21"))
            .andExpect(jsonPath("$.data.books[0].dday").value(2))
            .andExpect(jsonPath("$.data.books[0].bookId").doesNotExist())
            .andExpect(jsonPath("$.data.books[0].isbn").doesNotExist())
            .andExpect(jsonPath("$.data.books[0].author").doesNotExist());

        verify(currentReadingBookService).getCurrentReadingBooks("user-pk");
    }

    @Test
    @WithMockUser(username = "user-pk")
    @DisplayName("표지가 없는 도서는 표지 이미지 URL이 빈 문자열로 내려간다.")
    void getCurrentReadingBooks_ReturnsEmptyCoverImageUrl() throws Exception {
        CurrentReadingBooksResponse response = new CurrentReadingBooksResponse(List.of(
            new CurrentReadingBookResponse("group-123", "달러구트 꿈 백화점", null)
        ));
        given(currentReadingBookService.getCurrentReadingBooks("user-pk")).willReturn(response);

        mockMvc.perform(get("/api/v1/books/current-reading"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.books[0].title").value("달러구트 꿈 백화점"))
            .andExpect(jsonPath("$.data.books[0].coverImageUrl").value(""));
    }

    @Test
    @WithMockUser(username = "user-pk")
    @DisplayName("현재 배정된 책이 없으면 빈 목록을 반환한다.")
    void getCurrentReadingBooks_ReturnsEmptyList() throws Exception {
        given(currentReadingBookService.getCurrentReadingBooks("user-pk"))
            .willReturn(new CurrentReadingBooksResponse(List.of()));

        mockMvc.perform(get("/api/v1/books/current-reading"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.books").isEmpty());
    }

    @Test
    @WithMockUser
    @DisplayName("관심 도서 목록 조회 응답에 상세 페이지 이동용 ISBN과 표지 이미지가 포함된다.")
    void getInterestBooks_ContainsIsbnAndCoverImage() throws Exception {
        InterestBookResponseDto interestBook = new InterestBookResponseDto(
                1L,
                "9788954681179",
                "밝은 밤",
                "최은영",
                "https://image.aladin.co.kr/cover.jpg",
                LocalDateTime.of(2026, 7, 14, 12, 0)
        );
        given(bookService.getInterestBooks(any(), any(), any()))
                .willReturn(CursorPageResponse.of(List.of(interestBook), 30, 1L, false, null));

        mockMvc.perform(get("/api/v1/books/likes")
                        .param("cursor", "encoded-cursor")
                        .param("size", "10")
                        .with(user("user-pk")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].isbn").value("9788954681179"))
                .andExpect(jsonPath("$.data.content[0].title").value("밝은 밤"))
                .andExpect(jsonPath("$.data.content[0].coverImageUrl").value("https://image.aladin.co.kr/cover.jpg"))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());

        // 커서/size/로그인 사용자가 서비스로 그대로 전달되는지 확인한다.
        verify(bookService).getInterestBooks("encoded-cursor", 10, "user-pk");
    }

    @Test
    @WithMockUser
    @DisplayName("관심 도서가 없으면 빈 목록을 반환한다.")
    void getInterestBooks_ReturnsEmptyList() throws Exception {
        given(bookService.getInterestBooks(any(), any(), any()))
                .willReturn(CursorPageResponse.of(List.of(), 30, 0L, false, null));

        mockMvc.perform(get("/api/v1/books/likes"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @WithMockUser
    @DisplayName("Community Comment: add book like success")
    void addBookLike_Success() throws Exception {
        String isbn = "9788966263158";

        mockMvc.perform(post("/api/v1/books/{isbn}/likes", isbn)
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser
    @DisplayName("Community Comment: create review returns created review")
    void addBookReview_Success() throws Exception {
        String isbn = "9788966263158";
        given(bookService.createReview(any(), any(), any())).willReturn(sampleReview(isbn));

        String body = """
                {
                  "comment": "Great read",
                  "isHidden": false
                }
                """;

        mockMvc.perform(post("/api/v1/books/{isbn}/reviews", isbn)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reviewId").value(1L))
                .andExpect(jsonPath("$.data.reviewerName").value("reviewer"))
                .andExpect(jsonPath("$.data.profileImageUrl").value("https://cdn.example.com/profile.png"))
                .andExpect(jsonPath("$.data.isRecommended").value(true));
    }

    @Test
    @WithMockUser
    @DisplayName("Community Comment: get review list with sort/pagination")
    void getReviewList_Success() throws Exception {
        String isbn = "9788966263158";
        Page<ReviewDetailResponse> page = new PageImpl<>(List.of(sampleReview(isbn)));
        // sort/page/size 파라미터가 서비스로 정확히 전달되는지 eq 매처로 검증한다.
        given(bookService.getReviewList(eq(isbn), eq("popular"), eq(0), eq(10), any())).willReturn(page);

        mockMvc.perform(get("/api/v1/books/{isbn}/reviews", isbn)
                        .param("sort", "popular")
                        .param("page", "0")
                        .param("size", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].reviewId").value(1L))
                .andExpect(jsonPath("$.data.content[0].profileImageUrl").value("https://cdn.example.com/profile.png"))
                .andExpect(jsonPath("$.data.content[0].isLikedByMe").value(true))
                .andExpect(jsonPath("$.data.content[0].likeCount").value(3))
                // Spring Page는 hasNext 대신 페이지 정보(last/totalPages/number)를 제공한다. (hasNext = !last)
                .andExpect(jsonPath("$.data.last").exists())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.number").value(0));
    }

    @Test
    @WithMockUser
    @DisplayName("Community Comment: get review list defaults to latest sort")
    void getReviewList_DefaultSort() throws Exception {
        String isbn = "9788966263158";
        Page<ReviewDetailResponse> page = new PageImpl<>(List.of(sampleReview(isbn)));
        given(bookService.getReviewList(eq(isbn), eq("latest"), eq(0), eq(10), any())).willReturn(page);

        mockMvc.perform(get("/api/v1/books/{isbn}/reviews", isbn))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].reviewId").value(1L));
    }

    @Test
    @WithMockUser
    @DisplayName("Community Comment: get review stats includes myVote")
    void getReviewStats_Success() throws Exception {
        String isbn = "9788966263158";
        ReviewStatsResponse response = new ReviewStatsResponse(70, 30, VoteType.RECOMMEND);
        given(bookService.getReviewStats(eq(isbn), any())).willReturn(response);

        mockMvc.perform(get("/api/v1/books/{isbn}/reviews/stats", isbn))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendedRatio").value(70))
                .andExpect(jsonPath("$.data.notRecommendedRatio").value(30))
                .andExpect(jsonPath("$.data.myVote").value("RECOMMEND"));
    }

    @Test
    @WithMockUser
    @DisplayName("Community Comment: get review stats myVote null when no vote")
    void getReviewStats_NullMyVote() throws Exception {
        String isbn = "9788966263158";
        ReviewStatsResponse response = new ReviewStatsResponse(0, 0, null);
        given(bookService.getReviewStats(eq(isbn), any())).willReturn(response);

        mockMvc.perform(get("/api/v1/books/{isbn}/reviews/stats", isbn))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myVote").isEmpty());
    }

    @Test
    @WithMockUser
    @DisplayName("Community Comment: toggle review like returns state and count")
    void toggleReviewLike_Success() throws Exception {
        Long reviewId = 9L;
        given(bookService.toggleReviewLike(eq(reviewId), any()))
                .willReturn(ReviewLikeResponse.of(reviewId, true, 27));

        mockMvc.perform(post("/api/v1/books/reviews/{reviewId}/likes", reviewId)
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reviewId").value(9L))
                .andExpect(jsonPath("$.data.isLikedByMe").value(true))
                .andExpect(jsonPath("$.data.likeCount").value(27));
    }

    @Test
    @WithMockUser
    @DisplayName("Community Gallery: get gallery by isbn success")
    void getGalleryByIsbn_Success() throws Exception {
        String isbn = "9791161571188";
        GalleryResponseDto item = new GalleryResponseDto(
                10L,
                isbn,
                "https://cdn.example.com/thumb.jpg",
                4,
                LocalDateTime.of(2026, 6, 23, 12, 0)
        );
        CursorPageResponse<GalleryResponseDto> page =
                CursorPageResponse.of(List.of(item), 18, 1L, false, null);
        given(bookService.getGalleryByIsbn(eq(isbn), any(), any())).willReturn(page);

        mockMvc.perform(get("/api/v1/books/{isbn}/gallery", isbn))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].postId").value(10L))
                .andExpect(jsonPath("$.data.content[0].isbn").value(isbn));
    }
}
