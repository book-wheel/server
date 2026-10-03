package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "게시물 댓글 신고 요청")
public record PostCommentReportRequest(
    @Schema(description = "신고 사유", example = "ABUSE")
    @NotNull(message = "신고 사유는 필수입니다.") PostReportReason reason
) {}
