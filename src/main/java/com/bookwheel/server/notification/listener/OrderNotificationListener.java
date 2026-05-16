package com.bookwheel.server.notification.listener;

import com.bookwheel.server.notification.enums.NotificationType;
import com.bookwheel.server.notification.event.NotificationEvent;
import com.bookwheel.server.notification.support.NotificationText;
import com.bookwheel.server.order.event.ReadOrderAssignedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class OrderNotificationListener {

    private final ApplicationEventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReadOrderAssigned(ReadOrderAssignedEvent event) {
        String group = NotificationText.safe(event.groupName(), 30);
        for (String userId : event.orderedUserIds()) {
            eventPublisher.publishEvent(NotificationEvent.builder()
                    .recipientUserId(userId)
                    .type(NotificationType.READ_ORDER_ASSIGNED)
                    .title("독서 순서 확정")
                    .body("'" + group + "' 그룹의 독서 순서가 정해졌어요.")
                    .deepLink("/groups/" + event.groupId() + "/order")
                    .payload(Map.of("groupId", event.groupId()))
                    .build());
        }
    }
}