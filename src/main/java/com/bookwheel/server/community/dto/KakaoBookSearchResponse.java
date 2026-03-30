package com.bookwheel.server.community.dto;

import java.util.List;

public record KakaoBookSearchResponse(
    Meta meta,
    List<Document> documents
) {
    //메타 데이터 (페이징 처리 필요)
    public record Meta(
        Integer total_count,     // 전체 검색된 문서 수
        Integer pageable_count,  // 노출 가능한 문서 수
        Boolean is_end           // 현재 페이지가 마지막 페이지인지 여부
    ) {}

    // 2. 실제 책 데이터
    public record Document(
        String title,         // 도서 제목
        String contents,      // 도서 소개
        String url,           // 도서 상세 URL
        String isbn,          // ISBN
        List<String> authors, // 저자 리스트
        String publisher,     // 출판사
        String datetime,      // 출간일
        String thumbnail      // 표지 이미지 URL
    ) {}
}
