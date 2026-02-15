package com.bookwheel.server.book.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.LocalDate;

@Builder
public record OwnBookRegisterRequest(
        @NotBlank(message = "ISBN을 등록해주세요.")
        @Size(max = 20, message = "ISBN can be up to 20 characters.")
        String isbn,

        @NotBlank(message = "책 제목을 등록해주세요.")
        @Size(max = 255)
        String title,

        @Size(max = 100)
        String author,

        @Size(max = 100)
        String publisher,

        LocalDate pubDate,

        @Size(max = 255)
        String coverImage,

        @NotNull(message = "총 페이지 수를 등록해주세요.")
        @Min(value = 1)
        Integer totalPage,

        @Size(max = 100)
        String bookCondition,

        String noteToReader
) {
}
