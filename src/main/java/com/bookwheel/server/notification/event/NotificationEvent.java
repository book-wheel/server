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
        String recipientUserPK,
        NotificationType type,
        String title,
        String body,
        String deepLink,
        // 모임 알림 삭제와 비동기 저장 경합을 제어하기 위한 선택적 그룹 식별자다.
        String groupId,
        Map<String, Object> payload
) {
}
