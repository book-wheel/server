package com.bookwheel.server.notification.dto;

import com.bookwheel.server.notification.push.ExpoPushToken;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "알림 설정 부분 변경 요청. null 또는 생략한 필드는 기존 값을 유지합니다.")
public record NotificationPreferenceUpdateRequest(
        @Schema(description = "그룹 가입·멤버십 알림 수신 여부", example = "true", nullable = true)
        Boolean groupEnabled,

        @Schema(description = "라운드·일정·완독 알림 수신 여부", example = "true", nullable = true)
        Boolean roundEnabled,

        @Schema(description = "게시물·리뷰 커뮤니티 알림 수신 여부", example = "true", nullable = true)
        Boolean communityEnabled,

        @Schema(
                description = "푸시 알림 수신 여부. false여도 인앱 알림은 유지되며 계정·제재 푸시는 강제 발송됩니다.",
                example = "true",
                nullable = true
        )
        Boolean pushEnabled,

        @Schema(
                description = "Expo Push Token. 유효한 토큰은 등록·갱신하고 빈 문자열은 해제하며 null은 변경하지 않습니다.",
                example = "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]",
                nullable = true
        )
        @Size(max = 255, message = "Expo Push Token 길이가 너무 깁니다.")
        @Pattern(
                regexp = "^$|" + ExpoPushToken.REGEX,
                message = "Expo Push Token 형식이 올바르지 않습니다."
        )
        String expoPushToken
) {
}
