package com.bookwheel.server.admin.dto;

import jakarta.validation.constraints.NotNull;

public record AdminPostDeleteRequest(
    @NotNull(message = "삭제 사유는 필수입니다.")
    PostDeletionReason reason
) {}