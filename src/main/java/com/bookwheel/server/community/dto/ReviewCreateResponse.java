package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "리뷰 작성 결과 정보")
public record ReviewCreateResponse(
    @Schema(description = "작성된 리뷰 ID", example = "42")
    Long reviewId,

    @Schema(description = "도서 ID", example = "book-12345")
    String bookId,

    @Schema(description = "별점", example = "5")
    int rating,

    @Schema(description = "리뷰 내용", example = "이 책 덕분에 많이 웃었습니다. 다음 분도 재밌게 읽으시길!")
    String comment,

    @Schema(description = "히든 리뷰 상태", example = "true")
    boolean isHidden,

    @Schema(description = "작성 일시")
    LocalDateTime createdAt
)
{}
