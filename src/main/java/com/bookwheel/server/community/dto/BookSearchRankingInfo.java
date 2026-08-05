package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "도서 검색 결과 정렬 출처 정보")
public record BookSearchRankingInfo(

    @Schema(description = "검색 결과 정렬 출처", example = "DATA4LIBRARY")
    String source,

    @Schema(description = "정렬 출처 표시명", example = "도서관 정보나루 인기대출도서")
    String sourceName,

    @Schema(description = "외부 인기대출 데이터 집계 시작일. 카카오 기본 순서만 사용한 경우 null입니다.", example = "2026-07-01", nullable = true)
    LocalDate startDate,

    @Schema(description = "외부 인기대출 데이터 집계 종료일. 카카오 기본 순서만 사용한 경우 null입니다.", example = "2026-07-31", nullable = true)
    LocalDate endDate
) {

    public static BookSearchRankingInfo kakao() {
        return new BookSearchRankingInfo(
            "KAKAO",
            "카카오 도서 검색 API",
            null,
            null
        );
    }

    public static BookSearchRankingInfo data4Library(LocalDate startDate, LocalDate endDate) {
        return new BookSearchRankingInfo(
            "DATA4LIBRARY",
            "도서관 정보나루 인기대출도서",
            startDate,
            endDate
        );
    }
}
