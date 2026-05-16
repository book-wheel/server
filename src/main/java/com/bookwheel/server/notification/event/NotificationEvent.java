package com.bookwheel.server.notification.event;

import com.bookwheel.server.notification.enums.NotificationType;
import lombok.Builder;

import java.util.Map;

/**
 * 도메인 이벤트가 알림 시스템으로 변환된 후 발행되는 통합 이벤트.
 * 도메인 리스너가 이 이벤트를 발행하면 NotificationDispatcher 가 영속화 + 푸시 처리한다.
 */
@Builder
public record NotificationEvent(
        String recipientUserId,
        NotificationType type,
        String title,
        String body,
        String deepLink,
        Map<String, Object> payload
) {
}