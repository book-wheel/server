package com.bookwheel.server.community.controller;

import com.bookwheel.server.common.response.CursorPageResponse;
import com.bookwheel.server.community.dto.GalleryResponseDto;
import com.bookwheel.server.community.dto.ReviewDetailResponse;
import com.bookwheel.server.community.dto.ReviewLikeResponse;
import com.bookwheel.server.community.dto.ReviewStatsResponse;
import com.bookwheel.server.community.dto.VoteType;
import com.bookwheel.server.community.service.BookService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
