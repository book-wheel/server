package com.bookwheel.server.user.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.user.dto.UserBlockResponse;
import com.bookwheel.server.user.service.UserBlockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.bookwheel.server.common.util.SecurityUtil.getUserPK;

@RestController
@RequestMapping("/api/v1/users/me/blocks")
@RequiredArgsConstructor
@Tag(name = "사용자 차단", description = "내 차단 관계 관리 API")
public class UserBlockController {

    private final UserBlockService userBlockService;

    @Operation(summary = "사용자 차단", description = "반복 요청은 성공 처리합니다. 본인 차단은 BLOCK_001, "
        + "탈퇴한 대상은 BLOCK_002, 존재하지 않는 사용자는 AUTH_001입니다.")
    @PutMapping("/{userPK}")
    public ApiResponse<Void> block(
        @Parameter(description = "차단 대상 userPK") @PathVariable("userPK") String userPK,
        @AuthenticationPrincipal Object principal
    ) {
        userBlockService.block(getUserPK(principal), userPK);
        return ApiResponse.success(null);
    }

    @Operation(summary = "사용자 차단 해제", description = "내 차단 관계만 해제합니다. "
        + "대상이 탈퇴했거나 차단 관계가 없어도 성공합니다.")
    @DeleteMapping("/{userPK}")
    public ApiResponse<Void> unblock(
        @Parameter(description = "차단 해제 대상 userPK") @PathVariable("userPK") String userPK,
        @AuthenticationPrincipal Object principal
    ) {
        userBlockService.unblock(getUserPK(principal), userPK);
        return ApiResponse.success(null);
    }

    @Operation(summary = "내 차단 목록 조회", description = "차단 시각 내림차순, 동일 시 차단 ID 내림차순입니다. "
        + "탈퇴한 대상은 익명으로 표시하며 영구 삭제 시 목록에서 제거됩니다. "
        + "page는 0 이상, size는 1~50이며 범위 밖이면 COMMON_001입니다.")
    @GetMapping
    public ApiResponse<Page<UserBlockResponse>> getBlocks(
        @Parameter(description = "0부터 시작하는 페이지") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "페이지 크기 (1~50)") @RequestParam(defaultValue = "20") int size,
        @AuthenticationPrincipal Object principal
    ) {
        return ApiResponse.success(userBlockService.getBlocks(getUserPK(principal), page, size));
    }
}
