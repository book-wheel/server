package com.bookwheel.server.community.dto;

import jakarta.validation.constraints.NotNull;

public record PostReportRequest(
    @NotNull(message = "신고 사유는 필수입니다.")
    PostReportReason reason
)

{}
