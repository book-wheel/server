package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관심 도서 찜/취소 결과")
public record BookLikeResponse(
    @Schema(description = "도서 ISBN", example = "9791161571188")
    String isbn,

    @Schema(description = "찜 여부 (true: 찜함, false: 찜 취소)", example = "true")
    boolean liked
) {
    public static BookLikeResponse of(String isbn, boolean liked) {
        return new BookLikeResponse(isbn, liked);
    }
}
