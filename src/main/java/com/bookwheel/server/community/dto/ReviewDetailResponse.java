package com.bookwheel.server.community.dto;

import com.bookwheel.server.community.entity.BookReview;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "리뷰 상세 정보")
public record ReviewDetailResponse(
    @Schema(description = "리뷰 ID", example = "1")
    Long reviewId,

    @Schema(description = "도서 ISBN", example = "9791161571188")
    String isbn,

    @Schema(description = "리뷰어 닉네임", example = "문소희")
    String reviewerName,

    @Schema(description = "리뷰어 프로필 이미지 URL (없으면 null)", nullable = true)
    String profileImageUrl,

    @Schema(description = "추천 여부 (true: 추천, false: 비추천)", example = "true")
    boolean isRecommended,

    @Schema(description = "리뷰 내용", example = "리뷰 내용")
    String comment,

    @Schema(description = "히든 리뷰 여부", example = "false")
    boolean isHidden,

    @Schema(description = "공감 수", example = "26")
    int likeCount,

    @Schema(description = "로그인 사용자의 공감 여부", example = "true")
    boolean isLikedByMe,

    @Schema(description = "작성 일시")
    LocalDateTime createdAt
) {
    public static ReviewDetailResponse of(BookReview review, String profileImageUrl, boolean isLikedByMe) {
        return new ReviewDetailResponse(
            review.getReviewId(),
            review.getBookInfo().getIsbn(),
            review.getReviewer().getNickname(),
            profileImageUrl,
            review.getIsRecommended(),
            review.getContent(),
            review.getIsHidden(),
            review.getLikeCount(),
            isLikedByMe,
            review.getCreatedAt()
        );
    }
}
