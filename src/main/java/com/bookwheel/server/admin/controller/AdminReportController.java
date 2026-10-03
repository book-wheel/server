package com.bookwheel.server.admin.controller;

import com.bookwheel.server.admin.dto.*;
import com.bookwheel.server.admin.entity.ReportTargetType;
import com.bookwheel.server.admin.entity.ReportStatus;
import com.bookwheel.server.admin.service.AdminReportService;
import com.bookwheel.server.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
@Tag(name = "관리자 신고 관리")
public class AdminReportController {
    private final AdminReportService service;

    @GetMapping
    @Operation(summary = "신고 목록 조회", description = "type: POST/COMMENT, status: PENDING/RESOLVED/DISMISSED. "
        + "필터 생략 시 전체 조회. page는 0 이상, size는 1~50. 최신순으로 조회합니다.")
    public ApiResponse<Page<AdminReportResponse>> list(Authentication authentication,
            @RequestParam(required = false) ReportTargetType type,
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.list(authentication.getName(), type, status, page, size));
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "신고 상세 조회", description = "통합 신고 ID를 사용합니다. 삭제된 콘텐츠도 신고 당시 텍스트와 처리 이력을 조회할 수 있습니다. 이미지 URL은 원본이 존재할 때만 제공됩니다.")
    public ApiResponse<AdminReportDetailResponse> detail(Authentication authentication, @PathVariable Long reportId) {
        return ApiResponse.success(service.detail(authentication.getName(), reportId));
    }

    @PatchMapping("/{reportId}/process")
    @Operation(summary = "신고 처리 또는 기각", description = "처리된 신고는 다시 처리할 수 없습니다. "
        + "삭제된 콘텐츠의 삭제 조치는 REPORT_004, 탈퇴한 작성자의 제재는 REPORT_005입니다. 기각은 가능합니다.")
    public ApiResponse<AdminReportResponse> process(Authentication authentication, @PathVariable Long reportId,
            @Valid @RequestBody ReportProcessRequest request) {
        return ApiResponse.success(service.process(authentication.getName(), reportId, request));
    }
}
