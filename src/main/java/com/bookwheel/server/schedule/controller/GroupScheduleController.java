package com.bookwheel.server.schedule.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.schedule.dto.GroupScheduleCreateRequest;
import com.bookwheel.server.schedule.dto.GroupScheduleFutureRequest;
import com.bookwheel.server.schedule.dto.GroupScheduleResponse;
import com.bookwheel.server.schedule.dto.GroupScheduleRoundResponse;
import com.bookwheel.server.schedule.service.GroupScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
            description = "저장된 전체 날짜 틀과 현재 ACTIVE 멤버 기준 실행 범위, READY 차단 사유 및 내 책 배정을 조회합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "일정 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "모임 없음 또는 삭제된 모임 (GROUP_004, GROUP_049)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "ACTIVE 멤버가 아님 (GROUP_011)")
    })
    @GetMapping("/{groupId}/schedule")
    public ResponseEntity<ApiResponse<GroupScheduleResponse>> getSchedule(
            @PathVariable String groupId,
            @AuthenticationPrincipal Object principal
    ) {
        GroupScheduleResponse response = groupScheduleService.getSchedule(
                groupId,
                getUserPK(principal)
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "독서 일정 생성",
            description = "모집 중(RECRUITING)인 모임에 목표 인원 기준 라운드 날짜 틀을 저장합니다. " +
                    "현재 인원이나 도서 등록 여부와 관계없이 전체 틀은 생성합니다. ACTIVE 멤버가 2명 이상이고 " +
                    "전원이 책을 등록해 현재 인원 기준 N-1개 라운드 배정이 준비되면 목표 인원을 다 채우지 않아도 READY를 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "일정 생성 또는 기존 모집 일정 교체 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "상태·날짜·목표 인원·종료 제한·시작 당일 교체 오류 (GROUP_018, GROUP_019, GROUP_035, GROUP_048, GROUP_050, GROUP_052)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "리더 권한 없음 (GROUP_007)")
    })
    @PostMapping("/{groupId}/schedule")
    public ResponseEntity<ApiResponse<GroupScheduleResponse>> createSchedule(
            @PathVariable String groupId,
            @RequestBody @Valid GroupScheduleCreateRequest request,
            @AuthenticationPrincipal Object principal
    ) {
        GroupScheduleResponse response = groupScheduleService.createSchedule(
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
