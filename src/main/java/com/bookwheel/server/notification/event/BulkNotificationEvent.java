package com.bookwheel.server.notification.event;

import com.bookwheel.server.notification.enums.NotificationType;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * 동일 title/body 의 알림을 다수 수신자에게 한 번에 발송하기 위한 통합 이벤트.
 * NotificationDispatcher 가 단일 트랜잭션으로 saveAll + 멀티캐스트 푸시한다.
 *
 * 단건 알림은 {@link NotificationEvent} 를 그대로 사용한다.
 */
@Builder
public record BulkNotificationEvent(
        List<String> recipientUserPKs,
        NotificationType type,
        String title,
        String body,
        String deepLink,
        Map<String, Object> payload
) {
}
