package com.bookwheel.server.admin.controller;

import com.bookwheel.server.admin.dto.*;
import com.bookwheel.server.admin.service.AdminService;
import com.bookwheel.server.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "관리자(Admin) API", description = "신고 처리, 회원 제재, 사진 검수 등 관리자 전용 기능")
public class AdminController {

    private final AdminService adminService;

    @Operation(summary = "신고 목록 조회")
    @GetMapping("/reports")
    public ApiResponse<String> getReports() {
        return ApiResponse.success("신고목록 조회 api 연결 성공");
    }

    @Operation(summary = "신고처리")
    @PatchMapping("/reports/{reportId}/process")
    public ApiResponse<String> processReport(@PathVariable("reportId") Long reportId) {
        return ApiResponse.success(reportId +"신고처리 api 연결 성공");
    }


    @Operation(summary = "회원 강제 탈퇴/정지 시키기")
    @PostMapping("/users/{userPK}/ban")
    public ApiResponse<AdminBanResponse> banUser(
        @PathVariable("userPK") String userPK,
        @RequestBody AdminBanRequest request) {

        AdminBanResponse response = adminService.banUser(userPK, request);
        return ApiResponse.success(response);
    }

    @Operation(summary = "패널티 이력 조회", description = "특정 회원의 과거 제재 이력을 최신순으로 조회")
    @GetMapping("/users/{userPK}/histories")
    public ApiResponse<List<PenaltyResponse>> getPenaltyHistories(@PathVariable("userPK") String userPK) {
        List<PenaltyResponse> response = adminService.getPenalties(userPK);
        return ApiResponse.success(response);
    }

    @Operation(summary = "전체 게시물 목록 조회")
    @GetMapping("/post/list")
    public ApiResponse<List<AdminPostResponse>> getAllPost() {
        List<AdminPostResponse> response = adminService.getAllPost();
        return ApiResponse.success(response);
    }

    @Operation(summary = "사진 강제 삭제 (해당 게시물 전체 삭제)")
    @DeleteMapping("/post/{postId}")
    public ApiResponse<String> deletePhoto(
        @PathVariable("postId") Long postId,
        @Valid @RequestBody AdminPostDeleteRequest request) {

        adminService.deletePostByPost(postId, request);

        return ApiResponse.success(postId + "번 게시물이 [" + request.reason().getDescription() + "] 사유로 성공적으로 삭제되었습니다.");
    }
}

