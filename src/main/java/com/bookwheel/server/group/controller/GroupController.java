package com.bookwheel.server.group.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.group.dto.*;
import com.bookwheel.server.group.dto.member.*;
import com.bookwheel.server.group.dto.search.*;
import com.bookwheel.server.group.dto.setting.*;
import com.bookwheel.server.group.enums.Region;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.group.service.GroupService;
import com.bookwheel.server.member.service.MemberService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.bookwheel.server.common.util.SecurityUtil.getUserPK;
import static com.bookwheel.server.common.util.SecurityUtil.getUserPKOrNull;

@RestController
@RequiredArgsConstructor
@Tag(name = "Group", description = "그룹 생성/조회/가입/요청 관리 API")
@RequestMapping({"/api/v1/groups"})
public class GroupController {
    private final GroupService groupService;
    private final MemberService memberService;

    @Operation(summary = "그룹 생성", description = "새로운 독서 그룹을 생성합니다.")
    @PostMapping("/making")
    public ResponseEntity<ApiResponse<GroupCreateResponse>> createGroup(
            @RequestBody @Valid GroupCreateRequest groupCreateRequest,
            @AuthenticationPrincipal Object principal
    ) {
        GroupCreateResponse response = groupService.createGroup(groupCreateRequest, getUserPK(principal));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "그룹 목록 조회", description = "조건(상태/유형/지역/키워드)에 맞는 그룹 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<GroupSearchResponse>>> getGroups(
            @Parameter(description = "모집 상태", example = "RECRUITING")
            @RequestParam(required = false) State state,
            @Parameter(description = "모임 유형(ONLINE/OFFLINE)", example = "OFFLINE")
            @RequestParam(required = false) String type,
            @Parameter(description = "지역(OFFLINE일 때 사용)", example = "SEOUL")
            @RequestParam(required = false) Region region,
            @Parameter(description = "그룹명 검색어", example = "독서")
            @RequestParam(required = false) String keyword,
            @ParameterObject
            @PageableDefault(size = 10, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal Object principal
    ) {
        GroupSearchCondition condition = new GroupSearchCondition(state, type, region, keyword);
        // 로그인 여부에 따라 목록 버튼 상태 개인화 여부를 서비스에서 결정한다.
        Page<GroupSearchResponse> response = groupService.getGroups(condition, pageable, getUserPKOrNull(principal));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "내 모임 조회", description = "현재 로그인한 사용자가 ACTIVE 상태로 가입한 모임 목록을 최신 신청일 순으로 반환합니다.")
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<GroupSearchResponse>>> getMyGroups(
            @AuthenticationPrincipal Object principal
    ) {
        List<GroupSearchResponse> response = groupService.getMyGroups(getUserPK(principal));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "그룹 상세 조회", description = "특정 그룹의 상세 정보를 조회합니다.")
    @GetMapping("/{groupId}")
    public ResponseEntity<ApiResponse<GroupDetailResponse>> getGroup(
            @PathVariable String groupId,
            @AuthenticationPrincipal Object principal
    ) {
        GroupDetailResponse response = groupService.getGroup(groupId, getUserPK(principal));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "그룹 가입 신청",
            description = "그룹에 가입 신청을 보냅니다. 일정 목표 인원에 도달한 모임은 GROUP_051로 거절합니다."
    )
    @PostMapping("/{groupId}/join")
    public ResponseEntity<ApiResponse<GroupJoinResponse>> joinGroup(
            @PathVariable String groupId,
            @RequestBody @Valid GroupJoinRequest groupJoinRequest,
            @AuthenticationPrincipal Object principal
    ) {
        GroupJoinResponse response = groupService.joinGroup(groupId, groupJoinRequest, getUserPK(principal));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "가입 요청 목록 조회", description = "리더가 대기 중인 가입 요청 목록을 조회합니다.")
    @GetMapping("/{groupId}/members/requests")
    public ResponseEntity<ApiResponse<List<MemberRequestResponse>>> getMemberRequests(
            @PathVariable String groupId,
            @AuthenticationPrincipal Object principal
    ) {
        List<MemberRequestResponse> response = groupService.getMemberRequests(groupId, getUserPK(principal));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "가입 요청 처리",
            description = "모집 중(RECRUITING)인 모임에서 리더가 가입 요청을 승인 또는 거절합니다. 승인 후에도 목표 인원 기준 라운드는 유지되고 PLANNED 배정만 재검증됩니다. 목표 인원을 초과하는 승인은 GROUP_051로 거절되며, 프론트는 처리 후 GET /schedule을 다시 조회해 CONFIGURED/READY 상태를 갱신합니다."
    )
    @PatchMapping("/{groupId}/members/{memberId}/status")
    public ResponseEntity<ApiResponse<MemberRequestStatusUpdateResponse>> updateMemberRequestStatus(
            @PathVariable String groupId,
            @PathVariable String memberId,
            @RequestBody @Valid MemberRequestStatusUpdateRequest request,
            @AuthenticationPrincipal Object principal
    ) {
        MemberRequestStatusUpdateResponse response = groupService.updateMemberRequestStatus(
                groupId,
                memberId,
                getUserPK(principal),
                request.status()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "그룹 멤버 리스트",
            description = "특정 그룹의 ACTIVE 멤버 목록과 읽기 순서, 현재 라운드의 배정 도서 및 독서 상태를 조회합니다. " +
                    "요청자는 해당 그룹의 ACTIVE 멤버여야 하며, 현재 진행 중인 라운드 또는 멤버 배정이 없으면 해당 정보는 null입니다."
    )
    @GetMapping("/{groupId}/members")
    public ApiResponse<GroupMemberListResponse> getGroupMembers(
            @PathVariable String groupId,
            @AuthenticationPrincipal Object principal
    ) {
        GroupMemberListResponse response = memberService.getGroupMembers(groupId, getUserPK(principal));
        return ApiResponse.success(response);
    }
}
