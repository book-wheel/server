package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "도서 메인 화면의 현재 읽는 책 및 시작 예정 책 목록")
public record CurrentReadingBooksResponse(
    @Schema(description = "진행 중이거나 시작 예정인 교환독서 그룹에서 로그인한 사용자에게 배정된 도서 목록")
    List<CurrentReadingBookResponse> books
) {
    public CurrentReadingBooksResponse {
        books = books == null ? List.of() : List.copyOf(books);
    }
}
