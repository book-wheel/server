package com.bookwheel.server.schedule.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.schedule.dto.GroupScheduleCreateRequest;
import com.bookwheel.server.schedule.dto.GroupScheduleRoundResponse;
import com.bookwheel.server.schedule.service.GroupScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Group-Inner", description = "그룹 내부 활동 API (참여 도서 등록, 일정 생성, 읽기 순서 지정)")
@RequestMapping("/api/v1/groups")
public class GroupScheduleController {
    private final GroupScheduleService groupScheduleService;

    @Operation(
            summary = "독서 일정 생성",
            description = "시작일 기준으로 라운드 일정을 생성하거나 재생성합니다."
    )
    @PostMapping("/{groupId}/schedule")
    public ResponseEntity<ApiResponse<List<GroupScheduleRoundResponse>>> createSchedule(
            @PathVariable String groupId,
            @RequestBody @Valid GroupScheduleCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        List<GroupScheduleRoundResponse> response = groupScheduleService.createSchedule(
                groupId,
                request,
                userDetails.getUsername()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
