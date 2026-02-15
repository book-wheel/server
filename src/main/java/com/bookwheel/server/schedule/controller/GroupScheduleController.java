package com.bookwheel.server.schedule.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.schedule.dto.GroupScheduleCreateRequest;
import com.bookwheel.server.schedule.dto.GroupScheduleRoundResponse;
import com.bookwheel.server.schedule.service.GroupInnerScheduleService;
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
@RequestMapping("/api/groups")
public class GroupScheduleController {
    private final GroupInnerScheduleService groupInnerScheduleService;

    @PostMapping("/{groupId}/schedule")
    public ResponseEntity<ApiResponse<List<GroupScheduleRoundResponse>>> createSchedule(
            @PathVariable String groupId,
            @RequestBody @Valid GroupScheduleCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        List<GroupScheduleRoundResponse> response = groupInnerScheduleService.createSchedule(
                groupId,
                request,
                userDetails.getUsername()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
