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
                .title("회원 탈퇴 완료")
                .body("회원 탈퇴가 정상적으로 처리되었습니다. 계정 정보는 탈퇴 요청일부터 30일 후 영구 삭제됩니다.")
                .payload(Map.of())
                .build());
    }
}
