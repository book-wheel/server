package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "게시물 댓글 작성 요청")
public record PostCommentCreateRequest(

    @Schema(description = "댓글 내용", example = "어떻게 이런 발상을 하지 ㄷㄷ")
    @NotBlank(message = "댓글 내용을 입력해주세요.")
    String content
) {
}
