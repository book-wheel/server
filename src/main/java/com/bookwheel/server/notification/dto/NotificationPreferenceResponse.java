package com.bookwheel.server.notification.dto;

import com.bookwheel.server.notification.entity.NotificationPreference;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자 알림 수신 설정")
public record NotificationPreferenceResponse(
        @Schema(description = "그룹 가입·멤버십 알림 수신 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean groupEnabled,

        @Schema(description = "라운드·일정·완독 알림 수신 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean roundEnabled,

        @Schema(description = "게시물·리뷰 커뮤니티 알림 수신 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean communityEnabled,

        @Schema(description = "푸시 알림 수신 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean pushEnabled,

        @Schema(
                description = "현재 등록된 Expo Push Token. 등록된 디바이스가 없으면 null입니다.",
                example = "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]",
                nullable = true
        )
        String expoPushToken
) {
    public static NotificationPreferenceResponse from(NotificationPreference preference) {
        return new NotificationPreferenceResponse(
                Boolean.TRUE.equals(preference.getGroupEnabled()),
                Boolean.TRUE.equals(preference.getRoundEnabled()),
                Boolean.TRUE.equals(preference.getCommunityEnabled()),
                Boolean.TRUE.equals(preference.getPushEnabled()),
                preference.getExpoPushToken()
        );
    }
}
