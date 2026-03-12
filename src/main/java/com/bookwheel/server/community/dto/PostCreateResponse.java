package com.bookwheel.server.community.dto;

import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.entity.PostImage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "게시물 작성 결과 정보")
public record PostCreateResponse(
    @Schema(description = "작성된 게시물 ID", example = "105")
    Long postId,

    @Schema(description = "도서 ID", example = "book-12345")
    String bookId,

    @Schema(description = "감상평 내용", example = "양꼬치 먹고싶다 이 책 내용은 왜 양꼬치 언급이 없나요? 하 참;")
    String content,

    @Schema(description = "저장된 이미지 URL 목록")
    List<String> objectKeys
) {
    public static PostCreateResponse from(Post post) {
        return new PostCreateResponse(
            post.getPostId(),
            post.getBookInfo().getId(),
            post.getContent(),
            post.getImages().stream()
                .map(PostImage::getObjectKey)
                .toList()
        );
    }
}
