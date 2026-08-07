package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "관심 도서 목록 항목")
public record InterestBookResponseDto(
    @Schema(description = "도서 정보 ID", example = "1")
    Long bookInfoId,

    @Schema(description = "도서 ISBN (도서 상세 조회 요청에 사용)", example = "9791165341909")
    String isbn,

    @Schema(description = "도서 제목 (도서 데이터가 없으면 게시글 작성 시점에 저장된 제목으로 대체, 둘 다 없으면 null)",
        example = "달러구트 꿈 백화점", nullable = true)
    String title,

    @Schema(description = "저자 (도서 데이터가 없으면 null)", example = "이미예", nullable = true)
    String author,

    @Schema(description = "도서 표지 이미지 URL (없으면 null)",
        example = "https://image.aladin.co.kr/cover.jpg", nullable = true)
    String coverImageUrl,

    @Schema(description = "관심 도서로 등록한 일시 (이 값과 bookInfoId 기준으로 최신순 정렬)",
        example = "2026-07-14T12:00:00")
    LocalDateTime interestedAt
) {
}
