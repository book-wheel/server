package com.bookwheel.server.community.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.community.dto.PhotoReportRequest;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/photos")
@RequiredArgsConstructor
public class PhotoController {
    @Operation(summary = "사진첩 신고")
    @PostMapping("/{photoId}/reports")
    public ApiResponse<String> reportPhoto(@PathVariable("photoId") Long photoId,@RequestBody PhotoReportRequest request) {
        // TODO: 신고 사유(DTO) 받아서 Service로 넘기기
        return ApiResponse.success(photoId +"신고처리 api 연결 성공");
    }
}
