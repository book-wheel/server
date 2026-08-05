package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
@Schema(description = "도서 검색 결과 | 가공된 책 리스트, 전체 검색 결과 수, 마지막 페이지 여부 응답")
public record BookSearchListResponse(
    @Schema(description = "가공된 책 리스트")
    List<BookSearchResponse> books, // 가공된 책 리스트

    @Schema(description = "전체 검색 결과 수", example = "120")
    long totalCount,                // 전체 검색 결과 수

    @Schema(description = "마지막 페이지 여부", example = "false")
    boolean isEnd,                  // 마지막 페이지 여부

    @Schema(description = "검색 결과 정렬 출처 정보")
    BookSearchRankingInfo ranking   // 검색 결과 정렬 출처 정보
) {

    public BookSearchListResponse(List<BookSearchResponse> books, long totalCount, boolean isEnd) {
        this(books, totalCount, isEnd, BookSearchRankingInfo.kakao());
    }
}
