package com.bookwheel.server.community.dto;

import java.util.List;

public record NlkBookSearchResponse(
    List<NlkBook> result
) {
    public record NlkBook(
        String title_info,    // 표제 (제목)
        String author_info,   // 저작자
        String pub_info,      // 발행자
        String pub_year_info, // 발행년도
        String isbn,          // ISBN
        String detail_link    // 상세페이지 경로
    ) {}
}
