package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "관심 도서 목록 항목")
public record InterestBookResponseDto(
    @Schema(description = "도서 정보 ID", example = "1")
    Long bookInfoId,

    @Schema(description = "도서 제목", example = "달러구트 꿈 백화점")
    String title,

    @Schema(description = "저자", example = "이미예")
    String author,

    @Schema(description = "도서 표지 이미지 URL (없으면 null)", nullable = true)
    String coverImageUrl,

    @Schema(description = "관심 도서로 등록한 일시")
    LocalDateTime interestedAt
) {
}
