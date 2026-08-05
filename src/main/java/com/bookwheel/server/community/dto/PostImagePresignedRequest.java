package com.bookwheel.server.community.dto;


import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "게시물 사진 S3 Presigned URL 발급 요청")
public record PostImagePresignedRequest(
    @Schema(
        description = "업로드할 파일 확장자 목록 (최대 5개). jpg, jpeg, png, webp, heic, heif를 허용하며 대소문자는 구분하지 않는다.",
        example = "[\"jpg\", \"png\", \"HEIC\", \"heif\"]"
    )
    List<String> fileExtensions
) {
}
