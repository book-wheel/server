package com.bookwheel.server.chat.dto;

import jakarta.validation.constraints.NotNull;

public record ChatRoomReadRequest(
        @NotNull(message = "마지막으로 읽은 메시지 ID는 필수입니다.")
        Long lastReadMessageId
) {
}
