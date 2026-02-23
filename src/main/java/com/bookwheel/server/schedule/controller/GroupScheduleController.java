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
@Tag(name = "Group-Inner", description = "그룹 내부 활동 API")
@RequestMapping("/api/v1/groups")
public class GroupScheduleController {
    private final GroupScheduleService groupScheduleService;

    @Operation(
            summary = "독서 일정 생성",
            description = "시작일 기준으로 라운드 일정을 생성/재생성합니다. endDate는 최대 종료일(데드라인)이며 계산된 마지막 종료일이 이를 초과하면 실패합니다. excludedDates는 단일 날짜 제외, excludedDateRanges는 기간 제외(양끝 포함)입니다."
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
