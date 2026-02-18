package com.bookwheel.server.group.dto;

import jakarta.validation.constraints.NotNull;

public record MemberRequestStatusUpdateRequest(
        @NotNull(message = "승인/거절을 선택해야합니다.")
        MemberRequestStatus status
) {
}
