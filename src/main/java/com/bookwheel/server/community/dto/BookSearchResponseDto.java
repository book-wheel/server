package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "도서 검색 결과 응답 (프론트엔드 전달용)")
public record BookSearchResponseDto(
    @Schema(description = "도서 고유 식별자 (ISBN)", example = "9791161571188")
    String isbn,

    @Schema(description = "책 제목", example = "불편한 편의점")
    String title,

    @Schema(description = "저자", example = "김호연")
    String author,

    @Schema(description = "출판사", example = "나무옆의자")
    String publisher,

    @Schema(description = "출간일", example = "2021-04-20")
    String publishedDate,

    @Schema(description = "표지 이미지 URL", example = "https://image.aladin.co.kr/...")
    String coverImageUrl

    // boolean isLiked // TODO: 찜 기능 추가 시 주석 해제
)
{}
