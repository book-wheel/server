package com.bookwheel.server.community.controller;

import com.bookwheel.server.community.dto.ReviewDetailResponse;
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
    @DisplayName("Community Comment: get review list success")
    void getReviewList_Success() throws Exception {
        String isbn = "9788966263158";
        ReviewDetailResponse detailResponse = new ReviewDetailResponse(
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
        Page<ReviewDetailResponse> page = new PageImpl<>(List.of(detailResponse));
        given(bookService.getReviewList(eq(isbn), any(), anyInt(), anyInt(), any())).willReturn(page);

        mockMvc.perform(get("/api/v1/books/{isbn}/reviews", isbn))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].reviewId").value(1L));
    }

    @Test
    @WithMockUser
    @DisplayName("Community Comment: get review stats success")
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
    @DisplayName("Community Comment: toggle review like success")
    void toggleReviewLike_Success() throws Exception {
        Long reviewId = 9L;

        mockMvc.perform(post("/api/v1/books/reviews/{reviewId}/likes", reviewId)
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
