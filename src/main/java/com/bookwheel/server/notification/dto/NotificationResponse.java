package com.bookwheel.server.notification.dto;

import com.bookwheel.server.notification.entity.Notification;
import com.bookwheel.server.notification.enums.NotificationCategory;
import com.bookwheel.server.notification.enums.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "인앱 알림 정보")
public record NotificationResponse(
        @Schema(description = "알림 ID", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,

        @Schema(description = "알림 종류", example = "REVIEW_LIKED", requiredMode = Schema.RequiredMode.REQUIRED)
        NotificationType type,

        @Schema(description = "알림 설정 카테고리", example = "COMMUNITY", requiredMode = Schema.RequiredMode.REQUIRED)
        NotificationCategory category,

        @Schema(description = "알림 제목", example = "리뷰 공감", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @Schema(description = "알림 본문", example = "책벌레님이 회원님의 리뷰에 공감했어요.", requiredMode = Schema.RequiredMode.REQUIRED)
        String body,

        @Schema(description = "프론트엔드 이동 경로", example = "/reviews/9", nullable = true)
        String deepLink,

        @Schema(
                description = "인앱 화면 이동과 알림 종류별 상세 데이터. "
                        + "Expo Push는 여기서 notificationId, type, deepLink와 REVIEW_LIKED의 isbn만 전달",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        NotificationDataResponse data,

        @Schema(description = "읽음 여부", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean isRead,

        @Schema(description = "알림 생성 일시", example = "2026-08-14T10:30:00", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime createdAt,

        @Schema(description = "알림을 읽은 일시", example = "2026-08-14T10:35:00", nullable = true)
        LocalDateTime readAt
) {
    public static NotificationResponse from(Notification notification, Map<String, Object> data) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getCategory(),
                notification.getTitle(),
                notification.getBody(),
                notification.getDeepLink(),
                NotificationDataResponse.from(notification, data),
                Boolean.TRUE.equals(notification.getIsRead()),
                notification.getCreatedAt(),
                notification.getReadAt()
        );
    }
}
