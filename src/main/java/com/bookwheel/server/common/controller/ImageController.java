package com.bookwheel.server.common.controller;

import com.bookwheel.server.common.dto.ImagePresignedUrlResponse;
import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.common.service.S3Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
            description = "S3에 직접 파일을 업로드하기 위한 임시 주소와 업로드 완료 후 서버에 전달할 objectKey를 발급합니다. "
                    + "클라이언트는 presignedUrl을 파싱하지 말고 응답의 objectKey를 그대로 사용해야 합니다. "
                    + "profiles, profiles-temp prefix는 사용할 수 없으며 프로필 이미지는 전용 사용자 API를 사용해야 합니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "URL 발급 성공 (5분간 유효)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "presignedUrlResult",
                                    summary = "발급 결과",
                                    value = "{\n  \"success\": true,\n  \"data\": {\n    \"presignedUrl\": \"https://s3.bookwheel.kr/bucket/attachments/550e8400-e29b-41d4-a716-446655440000_my_photo.png?...\",\n    \"objectKey\": \"attachments/550e8400-e29b-41d4-a716-446655440000_my_photo.png\"\n  }\n}"
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "프로필 예약 prefix 사용 등 잘못된 요청 (COMMON_001)"
            )
    })
    @GetMapping("/presigned-url")
    public ApiResponse<ImagePresignedUrlResponse> getPresignedUrl(
            @Parameter(description = "저장 경로(프로필 예약 prefix 제외)", example = "attachments")
            @RequestParam String prefix,

            @Parameter(description = "원본 파일명", example = "my_photo.png")
            @RequestParam String fileName
    ) {
        return ApiResponse.success(s3Service.getPresignedUrl(prefix, fileName));
    }
}
