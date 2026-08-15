package com.bookwheel.server.notification.push;

import com.bookwheel.server.notification.entity.Notification;
import com.bookwheel.server.notification.enums.NotificationType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Expo Push data에 담는 프론트엔드 이동용 최소 데이터다.
 * 인앱 응답의 상세 payload와 분리해 제재 사유·이메일 등을 외부에 전송하지 않는다.
 */
public record ExpoPushData(
        Long notificationId,
        NotificationType type,
        String deepLink,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String isbn
) {

    public static ExpoPushData from(Notification notification, Map<String, Object> payload) {
        String isbn = null;
        if (notification.getType() == NotificationType.REVIEW_LIKED && payload != null) {
            Object isbnValue = payload.get("isbn");
            isbn = isbnValue == null ? null : isbnValue.toString();
        }
        return new ExpoPushData(
                notification.getId(),
                notification.getType(),
                notification.getDeepLink(),
                isbn
        );
    }
}
