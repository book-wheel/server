package com.bookwheel.server.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "안읽음 알림 개수")
public record UnreadCountResponse(
        @Schema(description = "현재 사용자의 안읽음 알림 수", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        long unreadCount
) {
}
