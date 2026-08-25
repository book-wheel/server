package com.bookwheel.server.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProfileImagePresignedUrlRequest(
        @Schema(description = "업로드할 프로필 이미지 파일명", example = "profile.png")
        @NotBlank(message = "파일명은 필수입니다.")
        String fileName,

        @Schema(description = "파일 MIME 타입", example = "image/png")
        @NotBlank(message = "Content-Type은 필수입니다.")
        String contentType,

        @Schema(description = "파일 크기(byte), 최대 5MB", example = "123456")
        @NotNull(message = "파일 크기는 필수입니다.")
        @Positive(message = "파일 크기는 0보다 커야 합니다.")
        Long fileSize
) {
}
