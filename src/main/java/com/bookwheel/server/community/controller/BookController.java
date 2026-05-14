package com.bookwheel.server.community.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.community.dto.*;
import com.bookwheel.server.community.service.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.bookwheel.server.common.util.SecurityUtil.getUserPK;

@RestController
@RequestMapping("/api/v1/books")
@RequiredArgsConstructor
@Tag(name = "도서 및 커뮤니티(코멘트) API", description = "도서 검색 -> 도서 상세 조회, 관심도서 찜 | 교환독서 책 리뷰 ")

public class BookController{
    private final BookService bookService;

    @Operation(summary = "도서 검색 (목록 조회)", description = "카카오 API를 활용해 가공된 도서 목록을 검색합니다.")
    @GetMapping("/search")
    public ApiResponse<BookSearchListResponse> searchBooks(@ModelAttribute BookSearchRequest request
    ) {
        BookSearchListResponse response = bookService.searchBooks(request);
        return ApiResponse.success(response);
    }


    @Operation(summary = "도서 상세 조회", description = "ISBN을 통해 도서의 상세 정보를 조회합니다.")
    @GetMapping("/{isbn}")
    public ApiResponse<BookDetailResponse> getBookDetail(@PathVariable("isbn") String isbn
    ) {
        BookDetailResponse response = bookService.getBookDetail(isbn);
        return ApiResponse.success(response);
    }


    @Operation(summary ="관심 도서 찜/취소")
    @PostMapping("/{isbn}/likes")
    public ApiResponse<String> addBookLike(@PathVariable("isbn") String isbn) {
        return ApiResponse.success(isbn + "관심 도서 찜 api 연결");

    }


    @Operation(summary = "리뷰 작성", description = "특정 책에 추천/비추천 여부와 함께 코멘트를 남깁니다.")
    @PostMapping("/{isbn}/reviews")
    public ApiResponse<String> addBookReview(
        @PathVariable("isbn") String isbn,
        @Valid @RequestBody ReviewCreateRequest request,
        @AuthenticationPrincipal Object principal) {
        bookService.createReview(request, getUserPK(principal));

        return ApiResponse.success("리뷰 작성이 완료되었습니다.");
    }

    @Operation(summary = "리뷰 목록 조회", description = "특정 책에 달린 모든 코멘트와 하트 개수, 내 공감 여부를 최신순으로 가져옵니다.")
    @GetMapping("/{isbn}/reviews")
    public ApiResponse<List<ReviewDetailResponse>> getReviewList(
        @PathVariable("isbn") String isbn,
        @AuthenticationPrincipal Object principal) {

        List<ReviewDetailResponse> response = bookService.getReviewList(isbn, getUserPK(principal));
        return ApiResponse.success(response);
    }

    @Operation(summary = "리뷰 추천/비추천 통계 조회", description = "특정 책의 전체 리뷰 중 추천/비추천 비율을 조회합니다.")
    @GetMapping("/{isbn}/reviews/stats")
    public ApiResponse<ReviewStatsResponse> getReviewStats(
        @PathVariable("isbn") String isbn) {

        ReviewStatsResponse response = bookService.getReviewStats(isbn);
        return ApiResponse.success(response);
    }

    @Operation(summary = "리뷰 공감(하트) 누르기/취소")
    @PostMapping("/reviews/{reviewId}/likes")
    public ApiResponse<String> toggleReviewLike(
        @PathVariable("reviewId") Long reviewId,
        @AuthenticationPrincipal Object principal) {

        bookService.toggleReviewLike(reviewId, getUserPK(principal));
        return ApiResponse.success("공감 상태가 변경되었습니다.");
    }

}
