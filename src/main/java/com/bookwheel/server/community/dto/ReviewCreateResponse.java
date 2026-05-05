package com.bookwheel.server.community.dto;

import com.bookwheel.server.community.entity.BookReview;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "리뷰 작성 결과 정보")
public record ReviewCreateResponse(
    @Schema(description = "작성된 리뷰 ID", example = "42")
    Long reviewId,

    @Schema(description = "도서 ISBN", example = "9788966263158")
    String isbn,

    //@Schema(description = "작성자 프로필 이미지 URL", example = "https://...")
    //String reviewerProfileImageKey, 추후 프론트에서 프로필 보일때 추가할게요!

    @Schema(description = "추천 여부", example = "true")
    boolean isRecommended,

    @Schema(description = "코멘트 내용", example = "이 책 덕분에 많이 웃었습니다. 다음 분도 재밌게 읽으시길!")
    String comment,

    @Schema(description = "히든 리뷰 상태", example = "true")
    boolean isHidden,

    @Schema(description = "작성 일시")
    LocalDateTime createdAt
)
{
    public static ReviewCreateResponse from(BookReview review) {
        return new ReviewCreateResponse(
            review.getReviewId(),
            review.getBookInfo().getIsbn(),
            review.getIsRecommended(),
            review.getContent(),
            review.getIsHidden(),
            review.getCreatedAt()
        );
    }
}
