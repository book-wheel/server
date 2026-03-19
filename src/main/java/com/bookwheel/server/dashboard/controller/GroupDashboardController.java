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

import static com.bookwheel.server.common.util.SecurityUtil.getUserId;

@RestController
@RequiredArgsConstructor
@Tag(name = "Group-Inner", description = "메인 대시보드 조회 API")
@RequestMapping("api/v1/groups")
public class GroupDashboardController {
    private final GroupDashboardService groupDashboardService;

    @Operation(
            summary = "그룹 대시보드 조회",
            description = "로그인한 사용자의 그룹 대시보드 정보를 조회합니다. 현재 라운드, D-Day, 내가 읽을 책(myStep), 내 책 전달 상태(myBookStep)를 반환합니다."
    )
    @GetMapping("/{groupId}/dashboard")
    public ApiResponse<DashboardResponse> getDashboard(
            @Parameter(description = "조회할 그룹 ID", example = "group-123")
            @PathVariable String groupId,
            @AuthenticationPrincipal Object principal
    ) {
        String userPK = getUserId(principal);
        DashboardResponse response = groupDashboardService.getDashboard(groupId, userPK);
        return ApiResponse.success(response);
    }
}
