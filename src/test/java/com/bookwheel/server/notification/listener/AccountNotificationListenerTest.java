package com.bookwheel.server.notification.listener;

import com.bookwheel.server.notification.enums.NotificationType;
import com.bookwheel.server.notification.event.NotificationEvent;
import com.bookwheel.server.user.event.UserDeactivatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AccountNotificationListenerTest {

    @Test
    @DisplayName("회원 탈퇴 알림은 복구를 안내하지 않고 30일 후 영구 삭제를 안내한다")
    void publishesCurrentWithdrawalPolicy() {
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        AccountNotificationListener listener = new AccountNotificationListener(eventPublisher);

        listener.onUserDeactivated(new UserDeactivatedEvent("user-pk"));

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        NotificationEvent notification = captor.getValue();
        assertThat(notification.recipientUserPK()).isEqualTo("user-pk");
        assertThat(notification.type()).isEqualTo(NotificationType.ACCOUNT_DEACTIVATED);
        assertThat(notification.title()).isEqualTo("회원 탈퇴 완료");
        assertThat(notification.body()).contains("30일 후 영구 삭제");
        assertThat(notification.body()).doesNotContain("복구");
        assertThat(notification.deepLink()).isNull();
    }
}
