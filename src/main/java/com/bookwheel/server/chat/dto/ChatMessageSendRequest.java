package com.bookwheel.server.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageSendRequest(
        @Schema(description = "텍스트 메시지 내용. 최대 1000자입니다.", example = "안녕하세요!", maxLength = MAX_CONTENT_LENGTH)
        @NotBlank(message = "메시지 내용은 필수입니다.")
        @Size(max = MAX_CONTENT_LENGTH, message = "메시지는 최대 1000자까지 입력할 수 있습니다.")
        String content
) {
    public static final int MAX_CONTENT_LENGTH = 1000;
}
