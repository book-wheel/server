package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "게시물 신고 요청 DTO")
public record PostReportRequest(
    @Schema(description = "신고 사유 (SPAM / ABUSE / PORNOGRAPHY / COPYRIGHT / OTHER)", example = "SPAM")
    @NotNull(message = "신고 사유는 필수입니다.")
    PostReportReason reason
)

{}
