package com.bookwheel.server.notification.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NotificationPreferenceUpdateRequest(
        Boolean groupEnabled,
        Boolean roundEnabled,
        Boolean communityEnabled,
        Boolean pushEnabled,

        // FCM 토큰은 base64url 계열 + 콜론/하이픈/언더바 정도만 허용. 길이는 컬럼 한도 이내.
        @Size(max = 255, message = "FCM 토큰 길이가 너무 깁니다.")
        @Pattern(regexp = "^[A-Za-z0-9_:\\-]+$", message = "FCM 토큰 형식이 올바르지 않습니다.")
        String fcmToken
) {
}