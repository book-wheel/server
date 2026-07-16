package com.bookwheel.server.schedule.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.schedule.dto.GroupScheduleCreateRequest;
import com.bookwheel.server.schedule.dto.GroupScheduleAssignmentResponse;
import com.bookwheel.server.schedule.dto.GroupScheduleFutureRequest;
import com.bookwheel.server.schedule.dto.GroupScheduleRoundResponse;
import com.bookwheel.server.schedule.service.GroupScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.bookwheel.server.common.util.SecurityUtil.getUserPK;

@RestController
@RequiredArgsConstructor
@Tag(name = "Group-Inner", description = "그룹 내부 활동 API")
@RequestMapping("/api/v1/groups")
public class GroupScheduleController {
    private final GroupScheduleService groupScheduleService;

    @Operation(
            summary = "내 독서 일정 조회",
            description = "라운드별 날짜와 저장된 내 책 배정, 책바퀴 상태를 조회합니다. 시작 전 미래 배정은 PLANNED 상태로 반환됩니다."
    )
    @GetMapping("/{groupId}/schedule")
    public ResponseEntity<ApiResponse<List<GroupScheduleAssignmentResponse>>> getSchedule(
            @PathVariable String groupId,
            @AuthenticationPrincipal Object principal
    ) {
        List<GroupScheduleAssignmentResponse> response = groupScheduleService.getSchedule(
                groupId,
                getUserPK(principal)
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "독서 일정 생성",
            description = "모집 중(RECRUITING)인 모임에서만 시작일과 독서 기간을 기준으로 라운드를 생성하거나 재생성합니다. endDate는 선택값이며, 시작일과 독서 기간은 이 API에서 함께 관리합니다."
    )
    @PostMapping("/{groupId}/schedule")
    public ResponseEntity<ApiResponse<List<GroupScheduleRoundResponse>>> createSchedule(
            @PathVariable String groupId,
            @RequestBody @Valid GroupScheduleCreateRequest request,
            @AuthenticationPrincipal Object principal
    ) {
        List<GroupScheduleRoundResponse> response = groupScheduleService.createSchedule(
                groupId,
                request,
                getUserPK(principal)
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "미래 독서 일정 재생성",
            description = "진행 중(IN_PROGRESS)인 모임에서 이미 시작된 라운드는 보존하고, 요청한 독서 기간으로 미래 라운드만 재생성합니다. 완료된 라운드의 기록과 시작일은 변경하지 않습니다."
    )
    @PostMapping("/{groupId}/schedule/future")
    public ResponseEntity<ApiResponse<List<GroupScheduleRoundResponse>>> regenerateFutureSchedule(
            @PathVariable String groupId,
            @RequestBody @Valid GroupScheduleFutureRequest request,
            @AuthenticationPrincipal Object principal
    ) {
        List<GroupScheduleRoundResponse> response = groupScheduleService.regenerateFutureSchedule(
                groupId,
                request,
                getUserPK(principal)
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
