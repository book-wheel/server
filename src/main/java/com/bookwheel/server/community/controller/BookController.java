package com.bookwheel.server.community.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.community.dto.ReviewCreateRequest;
import com.bookwheel.server.community.dto.ReviewDetailResponse;
import com.bookwheel.server.community.dto.ReviewStatsResponse;
import com.bookwheel.server.community.service.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/books")
@RequiredArgsConstructor
@Tag(name = "도서 및 커뮤니티(코멘트) API", description = "도서 검색 -> 도서 상세 조회, 관심도서 찜 | 교환독서 책 리뷰 ")

public class BookController{
    private final BookService bookService;

    @Operation(summary ="도서 검색")
    @GetMapping
    public ApiResponse<String> searchBooks() {
        // TODO: 검색어 파라미터 받기 (@RequestParam 등)
        return ApiResponse.success("도서 검색 api 연결");
    }

    @Operation(summary ="도서 상세 조회")
    @GetMapping("/{bookId}")
    public ApiResponse<String> getBookDetail(@PathVariable("bookId") String bookId) {
        return ApiResponse.success(bookId + "도서 상세 조회 api 연결");
    }




    @Operation(summary ="관심 도서 찜")
    @PostMapping("/{bookId}/likes")
    public ApiResponse<String> addBookLike(@PathVariable("bookId") String bookId) {
        return ApiResponse.success(bookId + "관심 도서 찜 api 연결");

    }

    @Operation(summary ="관심 도서 찜 취소")
    @DeleteMapping("/{bookId}/likes")
    public ApiResponse<String> deleteBookLike(@PathVariable("bookId") String bookId) {
        return ApiResponse.success(bookId + "관심 도서 찜 취소 api 연결");
    }

    @Operation(summary = "리뷰 작성", description = "특정 책에 추천/비추천 여부와 함께 코멘트를 남깁니다.")
    @PostMapping("/{bookId}/reviews")
    public ApiResponse<String> addBookReview(
        @PathVariable("bookId") String bookId,
        @Valid @RequestBody ReviewCreateRequest request,
        @AuthenticationPrincipal UserDetails userDetails) {

        bookService.createReview(bookId, request, userDetails.getUsername());

        return ApiResponse.success("리뷰 작성이 완료되었습니다.");
    }

    @Operation(summary = "리뷰 목록 조회", description = "특정 책에 달린 모든 코멘트와 하트 개수, 내 공감 여부를 최신순으로 가져옵니다.")
    @GetMapping("/{bookId}/reviews")
    public ApiResponse<List<ReviewDetailResponse>> getReviewList(
        @PathVariable("bookId") String bookId,
        @AuthenticationPrincipal UserDetails userDetails) {

        String userId = userDetails.getUsername();
        List<ReviewDetailResponse> response = bookService.getReviewList(bookId, userId);

        return ApiResponse.success(response);
    }

    @Operation(summary = "리뷰 추천/비추천 통계 조회", description = "특정 책의 전체 리뷰 중 추천/비추천 비율을 조회합니다.")
    @GetMapping("/{bookId}/reviews/stats")
    public ApiResponse<ReviewStatsResponse> getReviewStats(
        @PathVariable("bookId") String bookId) {

        ReviewStatsResponse response = bookService.getReviewStats(bookId);
        return ApiResponse.success(response);
    }

    @Operation(summary = "리뷰 공감(하트) 누르기/취소")
    @PostMapping("/reviews/{reviewId}/likes")
    public ApiResponse<String> toggleReviewLike(
        @PathVariable("reviewId") Long reviewId,
        @AuthenticationPrincipal UserDetails userDetails) {

        bookService.toggleReviewLike(reviewId, userDetails.getUsername());
        return ApiResponse.success("공감 상태가 변경되었습니다.");
    }

}
