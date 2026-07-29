package com.bookwheel.server.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ChatImageMessageSendRequest(
        @Schema(description = "Presigned URL 발급 응답으로 받은 S3 임시 objectKey",
                example = "chat-temp/chat-room-1/user-pk/550e8400-e29b-41d4-a716-446655440000.png")
        @NotBlank(message = "이미지 키는 필수입니다.")
        String imageKey
) {
}
