package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "도서 검색 결과 응답")
public record BookSearchResponse(

    @Schema(description = "책 제목", example = "불편한 편의점")
    String title,

    @Schema(description = "저자", example = "김호연")
    String author,

    @Schema(description = "출판사", example = "나무옆의자")
    String publisher,

    @Schema(description = "출간일", example = "2021-04-20")
    String publishedDate,

    @Schema(description = "표지 이미지 URL", example = "https://search.daum.net/search?w=bookpage&bookId=915087&q=%ED%95%B4%EC%BB%A4%EC%8A%A4+%ED%86%A0%EC%9D%B5+%EA%B8%B0%EC%B6%9C+%EB%B3%B4%EC%B9%B4%28TOEIC+VOCA%29")
    String thumbnail,

    @Schema(description = "도서 고유 식별자 (ISBN)", example = "9791161571188")
    String isbn

    // boolean isLiked // TODO: 찜 기능 추가 시 주석 해제
)
{
    public static BookSearchResponse of(KakaoBookSearchResponse.Document doc, String isbn13) {
        String date = doc.datetime();
        String processedDate = (date != null && date.length() >= 10) ? date.substring(0, 10) : date;
        return new BookSearchResponse(
            doc.title(),
            String.join(", ", doc.authors()),
            doc.publisher(),
            processedDate,
            doc.thumbnail(),
            isbn13
        );
    }

}
