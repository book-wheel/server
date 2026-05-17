package com.bookwheel.server.community.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.common.response.CursorPageResponse;
import com.bookwheel.server.community.dto.BookDetailResponse;
import com.bookwheel.server.community.dto.BookSearchListResponse;
import com.bookwheel.server.community.dto.BookSearchRequest;
import com.bookwheel.server.community.dto.GalleryResponseDto;
import com.bookwheel.server.community.dto.ReviewCreateRequest;
import com.bookwheel.server.community.dto.ReviewDetailResponse;
import com.bookwheel.server.community.dto.ReviewStatsResponse;
import com.bookwheel.server.community.service.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.bookwheel.server.common.util.SecurityUtil.getUserPK;

@RestController
@RequestMapping("/api/v1/books")
@RequiredArgsConstructor
@Tag(name = "Book Community", description = "Book search, reviews, likes, and gallery APIs")
public class BookController {

    private final BookService bookService;

    @Operation(summary = "Search books", description = "Searches books using the Kakao book API.")
    @GetMapping("/search")
    public ApiResponse<BookSearchListResponse> searchBooks(@ModelAttribute BookSearchRequest request) {
        BookSearchListResponse response = bookService.searchBooks(request);
        return ApiResponse.success(response);
    }

    @Operation(summary = "Gallery list", description = "Returns gallery posts with thumbnail images using cursor pagination.")
    @GetMapping("/gallery")
    public ApiResponse<CursorPageResponse<GalleryResponseDto>> getGallery(
        @Parameter(description = "Next page cursor")
        @RequestParam(required = false) String cursor,
        @Parameter(description = "Page size", example = "18")
        @RequestParam(required = false, defaultValue = "18") Integer size
    ) {
        CursorPageResponse<GalleryResponseDto> response = bookService.getGallery(cursor, size);
        return ApiResponse.success(response);
    }

    @Operation(summary = "Get book detail", description = "Returns book detail by ISBN.")
    @GetMapping("/{isbn}")
    public ApiResponse<BookDetailResponse> getBookDetail(@PathVariable("isbn") String isbn) {
        BookDetailResponse response = bookService.getBookDetail(isbn);
        return ApiResponse.success(response);
    }

    @Operation(summary = "Toggle book like")
    @PostMapping("/{isbn}/likes")
    public ApiResponse<String> addBookLike(@PathVariable("isbn") String isbn) {
        return ApiResponse.success(isbn + " interest book like API connected");
    }

    @Operation(summary = "Create review", description = "Creates a recommendation review for a book.")
    @PostMapping("/{isbn}/reviews")
    public ApiResponse<String> addBookReview(
        @PathVariable("isbn") String isbn,
        @Valid @RequestBody ReviewCreateRequest request,
        @AuthenticationPrincipal Object principal
    ) {
        bookService.createReview(request, getUserPK(principal));
        return ApiResponse.success("Review has been created.");
    }

    @Operation(summary = "Review list", description = "Returns reviews for a book.")
    @GetMapping("/{isbn}/reviews")
    public ApiResponse<List<ReviewDetailResponse>> getReviewList(
        @PathVariable("isbn") String isbn,
        @AuthenticationPrincipal Object principal
    ) {
        List<ReviewDetailResponse> response = bookService.getReviewList(isbn, getUserPK(principal));
        return ApiResponse.success(response);
    }

    @Operation(summary = "Review stats", description = "Returns recommendation stats for a book.")
    @GetMapping("/{isbn}/reviews/stats")
    public ApiResponse<ReviewStatsResponse> getReviewStats(@PathVariable("isbn") String isbn) {
        ReviewStatsResponse response = bookService.getReviewStats(isbn);
        return ApiResponse.success(response);
    }

    @Operation(summary = "Toggle review like")
    @PostMapping("/reviews/{reviewId}/likes")
    public ApiResponse<String> toggleReviewLike(
        @PathVariable("reviewId") Long reviewId,
        @AuthenticationPrincipal Object principal
    ) {
        bookService.toggleReviewLike(reviewId, getUserPK(principal));
        return ApiResponse.success("Review like status has been changed.");
    }
}
