package com.bookwheel.server.book.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.LocalDate;

@Builder
@Schema(description = "그룹 참여 도서 등록 요청")
public record OwnBookRegisterRequest(
        @Schema(description = "도서 ISBN", example = "9791190090018")
        @NotBlank(message = "ISBN을 등록해주세요.")
        @Size(max = 20, message = "ISBN can be up to 20 characters.")
        String isbn,

        @Schema(description = "도서 제목", example = "채식주의자")
        @NotBlank(message = "책 제목을 등록해주세요.")
        @Size(max = 255)
        String title,

        @Schema(description = "저자", example = "한강")
        @Size(max = 100)
        String author,

        @Schema(description = "출판사", example = "창비")
        @Size(max = 100)
        String publisher,

        @Schema(description = "출판일", example = "2007-10-30")
        LocalDate pubDate,

        @Schema(description = "표지 이미지 URL", example = "https://image.aladin.co.kr/product/...")
        @Size(max = 255)
        String coverImage,

        @Schema(description = "총 페이지 수", example = "250")
        @NotNull(message = "총 페이지 수를 등록해주세요.")
        @Min(value = 1)
        Integer totalPage,

        @Schema(description = "책 상태 메모", example = "이번에 새로 구매한 책입니다. 아주 깨끗해요")
        @Size(max = 100)
        String bookCondition,

        @Schema(description = "독자에게 전하는 메모", example = "제가 정말 아끼는 책입니다. 소중히 다뤄주세요!")
        String noteToReader
) {
}
