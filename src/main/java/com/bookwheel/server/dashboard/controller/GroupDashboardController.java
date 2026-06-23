package com.bookwheel.server.dashboard.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.dashboard.dto.DashboardResponse;
import com.bookwheel.server.dashboard.service.GroupDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.bookwheel.server.common.util.SecurityUtil.getUserPK;

@RestController
@RequiredArgsConstructor
@Tag(name = "Group-Inner", description = "메인 대시보드 조회 API")
@RequestMapping("api/v1/groups")
public class GroupDashboardController {
    private final GroupDashboardService groupDashboardService;

    @Operation(
            summary = "그룹 대시보드 조회",
            description = "시작 전에는 currentRound=0을 반환합니다. 책 등록 전 myBookStep은 null, 등록 후에는 내 책 정보를 반환합니다."
    )
    @GetMapping("/{groupId}/dashboard")
    public ApiResponse<DashboardResponse> getDashboard(
            @Parameter(description = "조회할 그룹 ID", example = "group-123")
            @PathVariable String groupId,
            @AuthenticationPrincipal Object principal
    ) {
        String userPK = getUserPK(principal);
        DashboardResponse response = groupDashboardService.getDashboard(groupId, userPK);
        return ApiResponse.success(response);
    }
}
