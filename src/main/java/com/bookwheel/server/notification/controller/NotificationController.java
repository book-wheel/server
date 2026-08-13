package com.bookwheel.server.notification.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.notification.dto.NotificationPreferenceResponse;
import com.bookwheel.server.notification.dto.NotificationPreferenceUpdateRequest;
import com.bookwheel.server.notification.dto.NotificationResponse;
import com.bookwheel.server.notification.dto.UnreadCountResponse;
import com.bookwheel.server.notification.service.NotificationPreferenceService;
import com.bookwheel.server.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

import static com.bookwheel.server.common.util.SecurityUtil.getUserPK;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notification", description = "인앱 알림 조회·읽음 처리, Expo Push Token 및 알림 수신 설정 API")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationPreferenceService preferenceService;

    @Operation(
            summary = "내 알림 목록 조회",
            description = "로그인 사용자의 알림을 최신순으로 조회합니다. "
                    + "data에는 푸시와 동일한 notificationId, type, deepLink와 알림 종류별 이동 정보가 포함됩니다. "
                    + "REVIEW_LIKED 알림에는 reviewId와 isbn이 함께 제공됩니다."
    )
    @GetMapping
    public ApiResponse<Page<NotificationResponse>> list(
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal Object principal
    ) {
        return ApiResponse.success(notificationService.list(getUserPK(principal), pageable));
    }

    @Operation(summary = "안읽음 알림 개수 조회", description = "로그인 사용자의 읽지 않은 인앱 알림 개수를 반환합니다.")
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount(@AuthenticationPrincipal Object principal) {
        return ApiResponse.success(notificationService.unreadCount(getUserPK(principal)));
    }

    @Operation(
            summary = "알림 한 건 읽음 처리",
            description = "본인에게 발송된 알림을 읽음 처리합니다. 이미 읽은 알림도 성공으로 처리합니다."
    )
    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(
            @Parameter(description = "읽음 처리할 알림 ID", example = "42", required = true)
            @PathVariable Long notificationId,
            @AuthenticationPrincipal Object principal
    ) {
        notificationService.markRead(getUserPK(principal), notificationId);
        return ApiResponse.success(null);
    }

    @Operation(
            summary = "모든 알림 읽음 처리",
            description = "로그인 사용자의 안읽음 알림을 모두 읽음 처리하고 실제로 변경된 알림 개수를 반환합니다."
    )
    @PatchMapping("/read-all")
    public ApiResponse<Integer> markAllRead(@AuthenticationPrincipal Object principal) {
        return ApiResponse.success(notificationService.markAllRead(getUserPK(principal)));
    }

    @Operation(
            summary = "내 알림 설정 조회",
            description = "카테고리별 수신 여부, 푸시 수신 여부와 현재 등록된 Expo Push Token을 조회합니다."
    )
    @GetMapping("/preferences")
    public ApiResponse<NotificationPreferenceResponse> getPreferences(@AuthenticationPrincipal Object principal) {
        return ApiResponse.success(preferenceService.get(getUserPK(principal)));
    }

    @Operation(
            summary = "내 알림 설정 변경",
            description = "카테고리·푸시 on/off와 Expo Push Token 등록·해제를 처리합니다. "
                    + "expoPushToken에 유효한 토큰을 전달하면 등록·갱신하고, 빈 문자열이면 해제하며, "
                    + "null 또는 생략하면 기존 값을 유지합니다. 다른 설정 필드도 null 또는 생략 시 기존 값을 유지합니다."
    )
    @PutMapping("/preferences")
    public ApiResponse<NotificationPreferenceResponse> updatePreferences(
            @Valid @RequestBody NotificationPreferenceUpdateRequest request,
            @AuthenticationPrincipal Object principal
    ) {
        if (request == null) {
            return ApiResponse.success(preferenceService.get(getUserPK(principal)));
        }
        return ApiResponse.success(preferenceService.update(getUserPK(principal), request));
    }
}
