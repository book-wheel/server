package com.bookwheel.server.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공통 이미지 S3 Presigned URL 발급 응답")
public record ImagePresignedUrlResponse(
        @Schema(description = "클라이언트가 파일을 PUT할 임시 URL",
                example = "https://s3.bookwheel.kr/bucket/attachments/550e8400-e29b-41d4-a716-446655440000_my_photo.png")
        String presignedUrl,

        @Schema(description = "업로드 완료 후 서버에 전달할 S3 객체 키 (Object Key)",
                example = "attachments/550e8400-e29b-41d4-a716-446655440000_my_photo.png")
        String objectKey
) {
}
