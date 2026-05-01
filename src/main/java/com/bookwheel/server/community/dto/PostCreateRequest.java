package com.bookwheel.server.community.dto;

import com.bookwheel.server.community.entity.BookInfo;
import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "도서 게시물(감상평+사진) 최종 등록 요청")
public record PostCreateRequest(

    @Schema(description = "알라딘 도서 고유 식별자(ISBN)", example = "9788966263158")
    @NotBlank(message = "ISBN은 필수입니다.")
    String isbn,

    @Schema(description = "감상평 내용", example = "이 페이지 진짜 너무 웃김 ㅋㅋㅋ")
    String content,

    @Schema(description = "S3에 업로드 완료된 이미지 객체 키 목록 (최대 5개)", example = "[\"posts/105/abcd_image.jpg\"]")
    @Size(max = 5, message = "사진은 최대 5장까지만 업로드할 수 있습니다.")
    List<String> objectKeys
) {

    public Post toEntity(BookInfo bookInfo, User uploader) {
        return Post.builder()
            .bookInfo(bookInfo)
            .uploader(uploader)
            .content(this.content)
            .build();
    }
}
