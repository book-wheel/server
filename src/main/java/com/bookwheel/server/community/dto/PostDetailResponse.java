package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "게시물 상세 정보")
public record PostDetailResponse(
    @Schema(description = "게시물 ID", example = "10")
    Long postId,

    @Schema(description = "도서 ISBN", example = "9791161571188")
    String isbn,

    @Schema(description = "작성자 닉네임", example = "문소희")
    String author,

    @Schema(description = "작성자 프로필 이미지 URL (없으면 null)", nullable = true)
    String profileImageUrl,

    @Schema(description = "모임 이름 (모임에서 작성한 글이면 모임명, 개인 작성이면 null)", nullable = true)
    String groupName,

    @Schema(description = "책 제목 (미등록 도서면 null)", nullable = true, example = "내 남편을 팝니다")
    String title,

    @Schema(description = "게시글 내용", example = "게시글 내용")
    String content,

    @Schema(description = "게시글 이미지 URL 목록")
    List<String> imageUrls,

    @Schema(description = "좋아요 수", example = "26")
    int likeCount,

    @Schema(description = "댓글 수", example = "3")
    long commentCount,

    @Schema(description = "로그인 사용자의 좋아요 여부", example = "true")
    boolean isLikedByMe,

    @Schema(description = "작성 일시")
    LocalDateTime createdAt
) {
}
