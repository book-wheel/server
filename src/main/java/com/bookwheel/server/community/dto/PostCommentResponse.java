package com.bookwheel.server.community.dto;

import com.bookwheel.server.community.entity.PostComment;
import com.bookwheel.server.community.support.CommunityAuthorDisplay;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "게시물 댓글 정보")
public record PostCommentResponse(
    @Schema(description = "댓글 ID", example = "1")
    Long commentId,

    @Schema(description = "게시물 ID", example = "10")
    Long postId,

    @Schema(description = "작성자 닉네임 (탈퇴 후 '탈퇴한 사용자')", example = "문소희")
    String author,

    @Schema(description = "작성자 프로필 이미지 URL. 작성자 탈퇴 시 null", nullable = true)
    String profileImageUrl,

    @Schema(description = "댓글 내용", example = "댓글 내용")
    String content,

    @Schema(description = "로그인 사용자가 작성한 댓글인지 여부. 작성자 탈퇴 시 false", example = "true")
    boolean isMine,

    @Schema(description = "작성 일시")
    LocalDateTime createdAt
) {
    public static PostCommentResponse of(PostComment comment, String profileImageUrl, boolean isMine) {
        return new PostCommentResponse(
            comment.getPostCommentId(),
            comment.getPost().getPostId(),
            CommunityAuthorDisplay.displayName(comment.getUser()),
            profileImageUrl,
            comment.getContent(),
            isMine,
            comment.getCreatedAt()
        );
    }
}
