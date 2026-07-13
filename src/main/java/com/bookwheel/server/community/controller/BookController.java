package com.bookwheel.server.community.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.common.response.CursorPageResponse;
import com.bookwheel.server.community.dto.BookDetailResponse;
import com.bookwheel.server.community.dto.BookLikeResponse;
import com.bookwheel.server.community.dto.BookSearchListResponse;
import com.bookwheel.server.community.dto.BookSearchRequest;
import com.bookwheel.server.community.dto.GalleryResponseDto;
import com.bookwheel.server.community.dto.InterestBookResponseDto;
import com.bookwheel.server.community.dto.ReviewCreateRequest;
import com.bookwheel.server.community.dto.ReviewDetailResponse;
import com.bookwheel.server.community.dto.ReviewLikeResponse;
import com.bookwheel.server.community.dto.ReviewStatsResponse;
import com.bookwheel.server.community.service.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.bookwheel.server.common.util.SecurityUtil.getUserPK;
import static com.bookwheel.server.common.util.SecurityUtil.getUserPKOrNull;

@RestController
@RequestMapping("/api/v1/books")
@RequiredArgsConstructor
@Tag(name = "도서 및 커뮤니티 API", description = "도서 검색, 도서 상세 조회, 관심 도서, 교환독서 갤러리, 리뷰 API")
public class BookController {

    private final BookService bookService;

    @Operation(summary = "도서 검색(목록 조회)", description = "카카오 API를 사용해 도서 목록을 검색합니다.")
    @GetMapping("/search")
    public ApiResponse<BookSearchListResponse> searchBooks(@ModelAttribute BookSearchRequest request) {
        BookSearchListResponse response = bookService.searchBooks(request);
        return ApiResponse.success(response);
    }

    @Operation(summary = "교환독서 갤러리 목록 조회", description = "게시물에 업로드된 대표 이미지를 최신순 커서 페이징으로 조회합니다.")
    @GetMapping("/gallery")
    public ApiResponse<CursorPageResponse<GalleryResponseDto>> getGallery(
        @Parameter(description = "다음 페이지 조회용 커서")
        @RequestParam(required = false) String cursor,
        @Parameter(description = "한 번에 조회할 갤러리 개수", example = "18")
        @RequestParam(required = false, defaultValue = "18") Integer size
    ) {
        CursorPageResponse<GalleryResponseDto> response = bookService.getGallery(cursor, size);
        return ApiResponse.success(response);
    }

    @Operation(summary = "관심 도서 목록 조회", description = "현재 로그인한 사용자가 관심 등록한 도서를 최근 등록순 커서 페이징으로 조회합니다.")
    @GetMapping("/likes")
    public ApiResponse<CursorPageResponse<InterestBookResponseDto>> getInterestBooks(
        @Parameter(description = "다음 페이지 조회용 커서")
        @RequestParam(required = false) String cursor,
        @Parameter(description = "한 번에 조회할 관심 도서 개수", example = "30")
        @RequestParam(required = false, defaultValue = "30") Integer size,
        @AuthenticationPrincipal Object principal
    ) {
        CursorPageResponse<InterestBookResponseDto> response = bookService.getInterestBooks(
            cursor,
            size,
            getUserPK(principal)
        );
        return ApiResponse.success(response);
    }

    @Operation(summary = "도서 상세 조회", description = "ISBN을 통해 도서 상세 정보를 조회합니다.")
    @GetMapping("/{isbn}")
    public ApiResponse<BookDetailResponse> getBookDetail(
        @PathVariable("isbn") String isbn,
        @AuthenticationPrincipal Object principal
    ) {
        BookDetailResponse response = bookService.getBookDetail(isbn, getUserPK(principal));
        return ApiResponse.success(response);
    }

    @Operation(summary ="관심 도서 찜/취소")
    @PostMapping("/{isbn}/likes")
    public ApiResponse<BookLikeResponse> addBookLike(
        @PathVariable("isbn") String isbn,
        @AuthenticationPrincipal Object principal
    ) {
        BookLikeResponse response = bookService.toggleBookLike(isbn, getUserPK(principal));
        return ApiResponse.success(response);
    }

    @Operation(summary = "리뷰 작성", description = "특정 책에 추천/비추천 여부와 코멘트를 작성하고, 생성된 리뷰 데이터를 반환합니다.")
    @PostMapping("/{isbn}/reviews")
    public ApiResponse<ReviewDetailResponse> addBookReview(
        @PathVariable("isbn") String isbn,
        @Valid @RequestBody ReviewCreateRequest request,
        @AuthenticationPrincipal Object principal
    ) {
        ReviewDetailResponse response = bookService.createReview(request, getUserPK(principal));
        return ApiResponse.success(response);
    }

    @Operation(summary = "리뷰 목록 조회", description = "특정 책에 달린 리뷰를 정렬 기준(latest/popular)과 페이지네이션으로 조회합니다. 하트 개수와 내 공감 여부, 다음 페이지 존재 여부를 포함합니다.")
    @GetMapping("/{isbn}/reviews")
    public ApiResponse<Page<ReviewDetailResponse>> getReviewList(
        @PathVariable("isbn") String isbn,
        @Parameter(description = "정렬 기준 (latest: 최신순, popular: 인기순)", example = "latest")
        @RequestParam(required = false, defaultValue = "latest") String sort,
        @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
        @RequestParam(required = false, defaultValue = "0") int page,
        @Parameter(description = "페이지 당 리뷰 개수", example = "10")
        @RequestParam(required = false, defaultValue = "10") int size,
        @AuthenticationPrincipal Object principal
    ) {
        Page<ReviewDetailResponse> response = bookService.getReviewList(isbn, sort, page, size, getUserPK(principal));
        return ApiResponse.success(response);
    }

    @Operation(summary = "리뷰 추천/비추천 통계 조회", description = "특정 책의 전체 리뷰 중 추천/비추천 비율과 로그인 사용자의 선택값(myVote)을 조회합니다.")
    @GetMapping("/{isbn}/reviews/stats")
    public ApiResponse<ReviewStatsResponse> getReviewStats(
        @PathVariable("isbn") String isbn,
        @AuthenticationPrincipal Object principal
    ) {
        ReviewStatsResponse response = bookService.getReviewStats(isbn, getUserPKOrNull(principal));
        return ApiResponse.success(response);
    }

    @Operation(summary = "리뷰 추천/비추천 공감 누르기/취소", description = "공감 상태를 토글하고 변경된 공감 여부와 공감 수를 반환합니다.")
    @PostMapping("/reviews/{reviewId}/likes")
    public ApiResponse<ReviewLikeResponse> toggleReviewLike(
        @PathVariable("reviewId") Long reviewId,
        @AuthenticationPrincipal Object principal
    ) {
        ReviewLikeResponse response = bookService.toggleReviewLike(reviewId, getUserPK(principal));
        return ApiResponse.success(response);
    }
}
