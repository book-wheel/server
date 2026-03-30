package com.bookwheel.server.community.dto;

import java.util.List;

public record AladinBookSearchResponse(
    List<Item> item
) {
    public record Item(
        String title,      // 제목
        String author,     // 저자
        String publisher,  // 출판사
        String pubDate,    // 출간일
        String isbn13,     // 13자리 ISBN
        String cover,      // 표지 이미지
        String description,// 책 소개
        SubInfo subInfo    // 부가 정보(목차)
    ) {}

    // 부가 정보 객체
    public record SubInfo(
        Integer itemPage,  // 쪽수
        String toc         // 목차
    ) {}
}
