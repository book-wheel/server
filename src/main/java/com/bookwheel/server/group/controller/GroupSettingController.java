package com.bookwheel.server.group.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.group.dto.setting.LeadershipTransferResponse;
import com.bookwheel.server.group.dto.setting.MemberKickResponse;
import com.bookwheel.server.group.dto.setting.MemberRoleChangeRequest;
import com.bookwheel.server.group.dto.setting.MemberRoleChangeResponse;
import com.bookwheel.server.group.service.GroupSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.bookwheel.server.common.util.SecurityUtil.getUserPK;

@RestController
@RequiredArgsConstructor
@Tag(name = "Group Setting", description = "그룹 설정 관련 API")
@RequestMapping("/api/v1/groups/{groupId}")
public class GroupSettingController {
    private final GroupSettingService groupSettingService;

    @Operation(summary = "멤버 강퇴", description = "리더가 그룹의 멤버를 강제 탈퇴시킵니다.")
    @DeleteMapping("/members/{targetUserPK}")
    public ResponseEntity<ApiResponse<MemberKickResponse>> kickMember(
            @PathVariable String groupId,
            @PathVariable String targetUserPK,
            @AuthenticationPrincipal Object principal
    ) {
        MemberKickResponse response = groupSettingService.kickMember(groupId, targetUserPK, getUserPK(principal));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "부방장 등록/해제", description = "리더가 그룹 멤버의 부방장 권한을 부여하거나 해제합니다.")
    @PatchMapping("/members/{targetUserPK}/role")
    public ResponseEntity<ApiResponse<MemberRoleChangeResponse>> changeMemberRole(
            @PathVariable String groupId,
            @PathVariable String targetUserPK,
            @AuthenticationPrincipal Object principal,
            @RequestBody @Valid MemberRoleChangeRequest request
    ) {
        MemberRoleChangeResponse response = groupSettingService.changeRole(groupId, targetUserPK, getUserPK(principal), request.memberRole());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "리더 위임", description = "현재 리더가 다른 ACTIVE 멤버에게 리더 권한을 넘기고 본인은 일반 멤버로 강등됩니다.")
    @PostMapping("/members/{targetUserPK}/leader")
    public ResponseEntity<ApiResponse<LeadershipTransferResponse>> transferLeadership(
            @PathVariable String groupId,
            @PathVariable String targetUserPK,
            @AuthenticationPrincipal Object principal
    ) {
        LeadershipTransferResponse response = groupSettingService.transferLeadership(groupId, targetUserPK, getUserPK(principal));
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
