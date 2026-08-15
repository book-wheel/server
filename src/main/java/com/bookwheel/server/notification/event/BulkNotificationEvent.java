package com.bookwheel.server.notification.event;

import com.bookwheel.server.notification.enums.NotificationType;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * 동일 title/body 의 알림을 다수 수신자에게 한 번에 발송하기 위한 통합 이벤트.
 * NotificationDispatcher가 이벤트를 전달하면 알림을 단일 트랜잭션으로 일괄 저장하고,
 * 커밋 후 별도 경로에서 Expo Push를 일괄 전송한다.
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
        // 여러 수신자에게 보내는 모임 알림도 동일한 그룹 범위를 가진다.
        String groupId,
        Map<String, Object> payload
) {
}
