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

    @Schema(description = "BookWheel 내부 관심 도서(찜) 수. 아직 아무도 찜하지 않았으면 0입니다.", example = "12")
    long likeCount,

    @Schema(description = "현재 로그인한 사용자의 관심 도서(찜) 여부", example = "true")
    boolean isInterested,

    @Schema(
        description = "대표 공개 후기. 공개 후기가 없으면 null입니다. "
            + "추천 도서는 전국 도서관 인기대출 순위에서 선정하므로 BookWheel 내부에 해당 ISBN의 후기가 없을 수 있고, "
            + "같은 책이라도 판본(ISBN)이 다르면 매칭되지 않습니다. "
            + "따라서 null이 예외 상황이 아니라 흔한 정상 응답이므로 후기 영역의 빈 상태 UI를 반드시 준비해 주시면 됩니다!!",
        nullable = true
    )
    BookExchangeRecommendationReview review
) {
}
