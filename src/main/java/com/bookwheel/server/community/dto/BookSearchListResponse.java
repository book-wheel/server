package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
@Schema(description = "도서 검색 결과 | 가공된 책 리스트, 전체 검색 결과 수, 마지막 페이지 여부 응답")
public record BookSearchListResponse(
    List<BookSearchResponse> books, // 가공된 책 리스트
    long totalCount,                // 전체 검색 결과 수
    boolean isEnd                   // 마지막 페이지 여부
) {}