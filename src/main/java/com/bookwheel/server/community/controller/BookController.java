package com.bookwheel.server.community.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.community.dto.ReviewCreateRequest;
import com.bookwheel.server.community.service.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/books")
@RequiredArgsConstructor
@Tag(name = "도서 및 커뮤니티 API", description = "도서 검색 -> 도서 상세 조회, 관심도서 찜 | 교환독서 책에 대한 리뷰 및 사진 업로드 ")

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

    @Operation(summary ="사진첩 업로드")
    @PostMapping("/{bookId}/photos")
    public ApiResponse<String> addBookPhoto(@PathVariable("bookId") String bookId, @RequestParam("file") MultipartFile file) {
        // TODO: 사진 파일과 사용자 정보 받아서 Service로 넘기기
        return ApiResponse.success(bookId + "사진첩 업로드 api 연결");
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

    @Operation(summary = "리뷰 작성")
    @PostMapping("/{bookId}/reviews")
    public ApiResponse<String> addBookReview(
        @PathVariable("bookId") String bookId,
        @Valid @RequestBody ReviewCreateRequest request,
        @AuthenticationPrincipal UserDetails userDetails) {

        bookService.createReview(bookId, request, userDetails.getUsername());

        return ApiResponse.success("리뷰 작성이 완료되었습니다.");
    }





}
