package com.bookwheel.server.community.dto;

import com.bookwheel.server.book.entity.Book;
import com.bookwheel.server.community.entity.BookInfo;
import com.bookwheel.server.community.entity.BookReview;
import com.bookwheel.server.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;


@Schema(description = "리뷰 작성 요청 DTO")
public record ReviewCreateRequest(

    @Schema(description = "알라딘 도서 고유 식별자(ISBN)", example = "9788966263158")
    @NotBlank(message = "ISBN은 필수입니다.")
    String isbn,

    @Schema(description = "한줄 후기/감상평 내용", example = "이 책 덕분에 많이 웃었습니다. 다음 분도 재밌게 읽으시길!")
    @NotBlank(message = "리뷰 내용은 필수 입니다.")
    String comment,

    @Schema(description = "추천 여부 (true: 추천, false: 비추천)", example = "true")
    @NotNull(message = "추천 여부는 필수 입니다.")
    Boolean isRecommended,

    @Schema(description = "히든 리뷰 여부 (true: 라운드 중 비공개, false: 공개)", example = "true")
    @NotNull(message = "히든 리뷰 설정 여부는 필수 입니다.")
    Boolean isHidden
)
{
    public BookReview toEntity(BookInfo bookInfo, User user) {
        return BookReview.builder()
            .bookInfo(bookInfo)
            .reviewer(user)
            .content(this.comment)
            .isRecommended(this.isRecommended)
            .isHidden(this.isHidden)
            .build();
    }
}
