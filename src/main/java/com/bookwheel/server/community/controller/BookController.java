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
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.bookwheel.server.common.util.SecurityUtil.getUserPK;

@RestController
@RequestMapping("/api/v1/books")
@RequiredArgsConstructor
@Tag(name = "도서 및 커뮤니티(코멘트) API", description = "도서 검색 -> 도서 상세 조회, 관심도서 찜 | 교환독서 책 리뷰 ")

public class BookController{
    private final BookService bookService;

    @Operation(summary = "도서 검색 (목록 조회)", description = "카카오 API를 활용해 도서를 검색합니다.")
    @GetMapping("/search")
    public ApiResponse<List<BookSearchResponse>> searchBooks(
        @RequestParam(name = "query") String query,                                   // 검색어 (제목, 저자 출판사 등)
        @RequestParam(name = "category", required = false) String category,           // 카테고리
        @RequestParam(name = "pubDate", required = false) String pubDate,             // 출간일 필터
        @RequestParam(name = "length", required = false) String length,               // 분량 (페이지 수)
        @RequestParam(name = "minRating", required = false) Integer minRating,        // 별점 (예: 4점 이상)
        @RequestParam(name = "excludeLiked", defaultValue = "false") boolean excludeLiked, // 관심도서 제외 체크박스
        @RequestParam(name = "page", defaultValue = "1") int page,
        @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        // TODO: bookService.searchBooks(query, page, size) 호출
        return ApiResponse.success("도서 검색 api 연결"); // 임시 리턴
    }




    @Operation(summary = "도서 상세 조회", description = "ISBN을 통해 도서의 상세 정보와 모임 정보를 조회합니다.")
    @GetMapping("/{isbn}")
    public ApiResponse<BookDetailResponse> getBookDetail(
        @PathVariable("isbn") String isbn
    ) {
        // TODO: bookService.getBookDetail(isbn) 호출
        return ApiResponse.success(isbn + "도서 상세 조회 api 연결"); // 임시 리턴
    }




    @Operation(summary ="관심 도서 찜/취소")
    @PostMapping("/{bookId}/likes")
    public ApiResponse<String> addBookLike(@PathVariable("bookId") String bookId) {
        return ApiResponse.success(bookId + "관심 도서 찜 api 연결");

    }


    @Operation(summary = "리뷰 작성", description = "특정 책에 추천/비추천 여부와 함께 코멘트를 남깁니다.")
    @PostMapping("/{bookId}/reviews")
    public ApiResponse<String> addBookReview(
        @PathVariable("bookId") String bookId,
        @Valid @RequestBody ReviewCreateRequest request,
        @AuthenticationPrincipal Object principal) {
        bookService.createReview(bookId, request, getUserPK(principal));

        return ApiResponse.success("리뷰 작성이 완료되었습니다.");
    }

    @Operation(summary = "리뷰 목록 조회", description = "특정 책에 달린 모든 코멘트와 하트 개수, 내 공감 여부를 최신순으로 가져옵니다.")
    @GetMapping("/{bookId}/reviews")
    public ApiResponse<List<ReviewDetailResponse>> getReviewList(
        @PathVariable("bookId") String bookId,
        @AuthenticationPrincipal Object principal) {

        List<ReviewDetailResponse> response = bookService.getReviewList(bookId, getUserPK(principal));
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
        @AuthenticationPrincipal Object principal) {

        bookService.toggleReviewLike(reviewId, getUserPK(principal));
        return ApiResponse.success("공감 상태가 변경되었습니다.");
    }

}
