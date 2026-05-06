package com.bookwheel.server.group.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.group.dto.setting.MemberKickResponse;
import com.bookwheel.server.group.service.GroupSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.bookwheel.server.common.util.SecurityUtil.getUserPK;

@RestController
@RequiredArgsConstructor
@Tag(name = "Group Setting", description = "그룹 설정 관련 API")
@RequestMapping("/api/v1/groups/{groupId}")
public class GroupSettingController {
    private final GroupSettingService groupSettingService;

    @Operation(summary = "멤버 강퇴", description = "리더가 그룹의 멤버를 강제 탈퇴시킵니다.")
    @DeleteMapping("/members/{memberId}")
    public ResponseEntity<ApiResponse<MemberKickResponse>> kickMember(
            @PathVariable String groupId,
            @PathVariable String memberId,
            @AuthenticationPrincipal Object principal
    ) {
        MemberKickResponse response = groupSettingService.kickMember(groupId, memberId, getUserPK(principal));
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
