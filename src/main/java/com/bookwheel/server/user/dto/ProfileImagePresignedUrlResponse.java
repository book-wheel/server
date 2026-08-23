package com.bookwheel.server.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProfileImagePresignedUrlResponse(
        @Schema(description = "S3 프로필 이미지 업로드용 Presigned PUT URL")
        String presignedUrl,

        @Schema(
                description = "setupProfile에 전달할 userPK 귀속 임시 S3 objectKey",
                example = "profiles-temp/550e8400-e29b-41d4-a716-446655440000/"
                        + "8d59e31d-25a4-4138-9b13-ffb692478a29.png"
        )
        String objectKey,

        @Schema(description = "Presigned PUT 요청에 사용할 Content-Type", example = "image/png")
        String contentType
) {
}
