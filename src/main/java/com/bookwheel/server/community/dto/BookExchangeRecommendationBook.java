package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "교환독서 추천도서 정보")
public record BookExchangeRecommendationBook(
    @Schema(description = "13자리 ISBN", example = "9788954681179")
    String isbn,

    @Schema(description = "도서 제목", example = "밝은 밤")
    String title,

    @Schema(description = "저자명", example = "최은영")
    String author,

    @Schema(description = "표지 이미지 URL. 정보나루가 표지를 제공하지 않으면 null입니다.", nullable = true)
    String coverImageUrl,

    @Schema(description = "정보나루 인기대출 순위", example = "1")
    int data4LibraryRank,

    @Schema(description = "집계 기간 동안의 정보나루 대출 건수", example = "104490")
    int data4LibraryLoanCount,

    @Schema(description = "BookWheel 내부 관심 도서(찜) 수", example = "12")
    long likeCount,

    @Schema(description = "현재 로그인한 사용자의 관심 도서(찜) 여부", example = "true")
    boolean isInterested,

    @Schema(description = "대표 공개 후기. 공개 후기가 없으면 null입니다.", nullable = true)
    BookExchangeRecommendationReview review
) {
}
