package com.bookwheel.server.notification.listener;

import com.bookwheel.server.admin.event.UserBannedEvent;
import com.bookwheel.server.notification.enums.NotificationType;
import com.bookwheel.server.notification.event.NotificationEvent;
import com.bookwheel.server.notification.support.NotificationText;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AdminNotificationListener {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ApplicationEventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserBanned(UserBannedEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("banType", event.banType());
        payload.put("reasonMessage", event.reasonMessage());

        String dateText;
        if (event.releaseDate() == null) {
            dateText = "기간 없음";
        } else if (event.releaseDate().getYear() == 9999) {
            dateText = "영구";
            payload.put("permanent", true);
        } else {
            dateText = event.releaseDate().format(DATE_FORMAT) + " 까지";
            payload.put("releaseDate", event.releaseDate().toString());
        }

        String safeReason = NotificationText.safe(event.reasonMessage(), 200);
        eventPublisher.publishEvent(NotificationEvent.builder()
                .recipientUserPK(event.userPK())
                .type(NotificationType.USER_BANNED)
                .title("계정 제재 안내")
                .body("사유: " + safeReason + " · 제재 기간: " + dateText)
                .deepLink("/account/penalties")
                .payload(payload)
                .build());
    }
}
