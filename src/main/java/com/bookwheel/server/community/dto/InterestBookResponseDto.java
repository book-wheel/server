package com.bookwheel.server.community.dto;

import java.time.LocalDateTime;

public record InterestBookResponseDto(
    Long bookId,              // 도서 ID
    String title,             // 도서 제목
    String author,            // 저자
    String coverImageUrl,     // 도서 표지 이미지 URL
    LocalDateTime interestedAt // 관심 도서로 등록한 일시
) {
}
