package com.bookwheel.server.notification.listener;

import com.bookwheel.server.notification.event.NotificationEvent;
import com.bookwheel.server.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 도메인 리스너가 변환해 발행한 NotificationEvent 를 영속화/푸시한다.
 * NotificationService.create() 가 자체 @Transactional 을 보유하므로 디스패처는
 * 비동기 스케줄링과 예외 격리만 담당한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationService notificationService;

    @Async
    @EventListener
    public void handle(NotificationEvent event) {
        try {
            notificationService.create(event);
        } catch (Exception e) {
            log.warn("알림 처리 실패: type={}, recipient={}, reason={}",
                    event.type(), event.recipientUserPK(), e.getMessage());
        }
    }
}
