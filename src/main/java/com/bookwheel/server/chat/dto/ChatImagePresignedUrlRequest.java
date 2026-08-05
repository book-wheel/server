package com.bookwheel.server.chat.dto;

import com.bookwheel.server.chat.image.ChatImagePolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ChatImagePresignedUrlRequest(
        @Schema(description = "원본 이미지 파일명", example = "cat.png")
        @NotBlank(message = "파일명은 필수입니다.")
        String fileName,

        @Schema(description = "이미지 MIME 타입", example = "image/png")
        @NotBlank(message = "MIME 타입은 필수입니다.")
        String contentType,

        @Schema(description = "이미지 파일 크기(byte)", example = "123456", maximum = "5242880")
        @NotNull(message = "파일 크기는 필수입니다.")
        @Positive(message = "파일 크기는 0보다 커야 합니다.")
        @Max(value = ChatImagePolicy.MAX_FILE_SIZE, message = "이미지는 최대 5MB까지 업로드할 수 있습니다.")
        Long fileSize
) {
}
