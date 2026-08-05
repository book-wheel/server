package com.bookwheel.server.community.dto;

import java.time.LocalDate;
import java.util.List;

public record BookSearchRankingResult(
    List<BookSearchResponse> books,
    BookSearchRankingInfo ranking
) {

    public static BookSearchRankingResult kakao(List<BookSearchResponse> books) {
        return new BookSearchRankingResult(books, BookSearchRankingInfo.kakao());
    }

    public static BookSearchRankingResult data4Library(
        List<BookSearchResponse> books,
        LocalDate startDate,
        LocalDate endDate
    ) {
        return new BookSearchRankingResult(
            books,
            BookSearchRankingInfo.data4Library(startDate, endDate)
        );
    }
}
