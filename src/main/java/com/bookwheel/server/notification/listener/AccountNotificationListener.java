package com.bookwheel.server.notification.listener;

import com.bookwheel.server.notification.enums.NotificationType;
import com.bookwheel.server.notification.event.NotificationEvent;
import com.bookwheel.server.user.event.UserDeactivatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AccountNotificationListener {

    private final ApplicationEventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserDeactivated(UserDeactivatedEvent event) {
        eventPublisher.publishEvent(NotificationEvent.builder()
                .recipientUserPK(event.userPK())
                .type(NotificationType.ACCOUNT_DEACTIVATED)
                .title("계정 탈퇴 처리")
                .body("회원 탈퇴가 정상적으로 처리되었습니다. 일정 기간 내 동일 계정으로 복구가 가능해요.")
                .deepLink("/account/recovery")
                .payload(Map.of("mail", event.mail() == null ? "" : event.mail()))
                .build());
    }
}
