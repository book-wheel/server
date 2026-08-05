package com.bookwheel.server.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
public record ChatImagePresignedUrlResponse(
        @Schema(description = "S3 이미지 업로드용 Presigned PUT URL")
        String presignedUrl,

        @Schema(description = "이미지 메시지 저장 시 전달할 S3 임시 objectKey",
                example = "chat-temp/chat-room-1/user-pk/550e8400-e29b-41d4-a716-446655440000.png")
        String objectKey
) {
}
