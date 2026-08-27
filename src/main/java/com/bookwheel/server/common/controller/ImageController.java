package com.bookwheel.server.common.controller;

import com.bookwheel.server.common.service.S3Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Common-Image", description = "공통 이미지 업로드 관련 API")
@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageController {

    private final S3Service s3Service;

    @Operation(
            summary = "S3 Presigned URL 발급",
            description = "S3에 직접 파일을 업로드하기 위한 임시 주소를 발급합니다. "
                    + "profiles, profiles-temp prefix는 사용할 수 없으며 프로필 이미지는 전용 사용자 API를 사용해야 합니다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "URL 발급 성공 (5분간 유효)",
            content = @Content(schema = @Schema(implementation = String.class))
    )
    @GetMapping("/presigned-url")
    public String getPresignedUrl(
            @Parameter(description = "저장 경로(프로필 예약 prefix 제외)", example = "attachments")
            @RequestParam String prefix,

            @Parameter(description = "원본 파일명", example = "my_photo.png")
            @RequestParam String fileName
    ) {
        return s3Service.getPresignedUrl(prefix, fileName);
    }
}
