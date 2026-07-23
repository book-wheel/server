package com.bookwheel.server.chat.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatMessageSendRequest(
        @NotBlank(message = "메시지 내용은 필수입니다.")
        String content
) {
}
