package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;


@Schema(description = "리뷰 작성 요청 DTO")
public record ReviewCreateRequest(
    @Schema(description = "별점 (1~5)", example = "5")
    @Min(value = 1, message = "별점은 1점 이상이어야 합니다.")
    @Max(value = 5, message = "별점은 5점 이하여야 합니다.")
    int rating,

        @Schema(description = "한줄 후기/감상평 내용", example = "이 책 덕분에 많이 웃었습니다. 다음 분도 재밌게 읽으시길!")
        @NotBlank(message = "리뷰 내용은 필수입니다.")
            String comment,

        @Schema(description = "히든 리뷰 여부 (true: 라운드 중 비공개, false: 공개)", example = "true")
        @NotNull(message = "히든 리뷰 설정 여부는 필수입니다.")
            Boolean isHidden
) {}
