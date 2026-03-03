package com.bookwheel.server.community.dto;

import com.bookwheel.server.book.entity.Book;
import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "도서 게시물(감상평+사진) 최종 등록 요청")
public record PostCreateRequest(
    @Schema(description = "감상평 내용", example = "이 페이지 진짜 너무 웃김 ㅋㅋㅋ")
    String content,

    @Schema(description = "S3에 업로드 완료된 이미지 URL 목록 (최대 5개)", example = "[\"https://.../uuid1.jpg\", \"https://.../uuid2.jpg\"]")
    List<String> imageUrls
) {

    public Post toEntity(Book book, User uploader) {
        return Post.builder()
            .book(book)
            .uploader(uploader)
            .content(this.content)
            .build();
    }
}
