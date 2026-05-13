package com.bookwheel.server.community.dto;

import java.time.LocalDateTime;

public record GalleryResponseDto(
    Long galleryId,          // 갤러리 게시글 ID
    Long bookId,             // 갤러리가 연결된 도서 ID
    String thumbnailUrl,     // 갤러리 대표 이미지 URL
    int imageCount,          // 포함된 이미지 개수
    LocalDateTime createdAt  // 갤러리 생성 일시
) {
}
