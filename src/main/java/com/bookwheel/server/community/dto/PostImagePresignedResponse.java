package com.bookwheel.server.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "게시물 사진 S3 Presigned URL 발급 응답")
public record PostImagePresignedResponse(
    @Schema(description = "발급된 URL 정보 목록")
    List<PresignedInfo> presignedUrls
) {
    @Schema(description = "단일 이미지 URL 정보")
    public record PresignedInfo(
        @Schema(description = "클라이언트가 파일을 PUT할 임시 URL", example = "https://bucket.s3.ap-northeast-2.amazonaws.com/...")
        String presignedUrl,

        @Schema(description = "업로드 완료 후 DB에 저장할 S3 객체 키 (Object Key)", example = "posts/105/abcd_image.jpg")
        String objectKey
    ) {}
}
