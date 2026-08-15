package com.bookwheel.server.notification.service;

import com.bookwheel.server.notification.push.PushSender;
import com.bookwheel.server.notification.push.PushTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPushService {

    private final NotificationPushTargetResolver targetResolver;
    private final PushSender pushSender;

    /**
     * 알림 저장 커밋 후 별도 스레드에서 실제 푸시를 발송한다.
     * 알림 ID만 인자로 받고 현재 토큰 소유자와 푸시 설정을 다시 조회한다.
     */
    @Async("notificationTaskExecutor")
    public void send(List<Long> notificationIds) {
        try {
            List<PushTarget> targets = targetResolver.resolve(notificationIds);
            if (!targets.isEmpty()) {
                pushSender.sendBatch(targets);
            }
        } catch (Exception exception) {
            int requestedCount = notificationIds == null ? 0 : notificationIds.size();
            log.warn("Expo Push 발송 처리 실패: requested={}, reason={}",
                    requestedCount, exception.getMessage());
        }
    }
}
