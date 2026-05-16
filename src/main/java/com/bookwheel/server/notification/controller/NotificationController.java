package com.bookwheel.server.notification.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.notification.dto.NotificationPreferenceResponse;
import com.bookwheel.server.notification.dto.NotificationPreferenceUpdateRequest;
import com.bookwheel.server.notification.dto.NotificationResponse;
import com.bookwheel.server.notification.dto.UnreadCountResponse;
import com.bookwheel.server.notification.service.NotificationPreferenceService;
import com.bookwheel.server.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.bookwheel.server.common.util.SecurityUtil.getUserId;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notification", description = "인앱 알림 조회/읽음/사용자 알림 설정 API")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationPreferenceService preferenceService;

    @Operation(summary = "내 알림 목록 조회 (최신순)")
    @GetMapping
    public ApiResponse<Page<NotificationResponse>> list(
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal Object principal
    ) {
        return ApiResponse.success(notificationService.list(getUserId(principal), pageable));
    }

    @Operation(summary = "안읽음 알림 개수")
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount(@AuthenticationPrincipal Object principal) {
        return ApiResponse.success(notificationService.unreadCount(getUserId(principal)));
    }

    @Operation(summary = "알림 읽음 처리")
    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal Object principal
    ) {
        notificationService.markRead(getUserId(principal), notificationId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "모든 알림 읽음 처리")
    @PatchMapping("/read-all")
    public ApiResponse<Integer> markAllRead(@AuthenticationPrincipal Object principal) {
        return ApiResponse.success(notificationService.markAllRead(getUserId(principal)));
    }

    @Operation(summary = "내 알림 설정 조회")
    @GetMapping("/preferences")
    public ApiResponse<NotificationPreferenceResponse> getPreferences(@AuthenticationPrincipal Object principal) {
        return ApiResponse.success(preferenceService.get(getUserId(principal)));
    }

    @Operation(summary = "내 알림 설정 변경 (카테고리 on/off, FCM 토큰 등록)")
    @PutMapping("/preferences")
    public ApiResponse<NotificationPreferenceResponse> updatePreferences(
            @Valid @RequestBody NotificationPreferenceUpdateRequest request,
            @AuthenticationPrincipal Object principal
    ) {
        if (request == null) {
            return ApiResponse.success(preferenceService.get(getUserId(principal)));
        }
        return ApiResponse.success(preferenceService.update(getUserId(principal), request));
    }
}