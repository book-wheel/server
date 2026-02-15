package com.bookwheel.server.group.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.group.dto.*;
import com.bookwheel.server.group.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/groups"})
public class GroupController {
    private final GroupService groupService;

    @PostMapping("/making")
    public ResponseEntity<ApiResponse<GroupCreateResponse>> createGroup(
            @RequestBody @Valid GroupCreateRequest groupCreateRequest,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        GroupCreateResponse response = groupService.createGroup(groupCreateRequest, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<GroupSearchResponse>>> getGroups(
            @ModelAttribute GroupSearchCondition condition,
            @PageableDefault(size = 10, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<GroupSearchResponse> response = groupService.getGroups(condition, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<ApiResponse<GroupDetailResponse>> getGroup(@PathVariable String groupId) {
        GroupDetailResponse response = groupService.getGroup(groupId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{groupId}/join")
    public ResponseEntity<ApiResponse<GroupJoinResponse>> joinGroup(
            @PathVariable String groupId,
            @RequestBody @Valid GroupJoinRequest groupJoinRequest,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        GroupJoinResponse response = groupService.joinGroup(groupId, groupJoinRequest, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{groupId}/members/requests")
    public ResponseEntity<ApiResponse<List<MemberRequestResponse>>> getMemberRequests(
            @PathVariable String groupId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        List<MemberRequestResponse> response = groupService.getMemberRequests(groupId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{groupId}/members/{memberId}/status")
    public ResponseEntity<ApiResponse<MemberRequestStatusUpdateResponse>> updateMemberRequestStatus(
            @PathVariable String groupId,
            @PathVariable String memberId,
            @RequestBody @Valid MemberRequestStatusUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        MemberRequestStatusUpdateResponse response = groupService.updateMemberRequestStatus(
                groupId,
                memberId,
                userDetails.getUsername(),
                request.status()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
