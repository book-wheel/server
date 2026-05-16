package com.bookwheel.server.notification.push;

import com.bookwheel.server.notification.entity.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 기본 푸시 어댑터. 실제 FCM 어댑터를 추가할 때는 해당 빈을 {@code @Primary}로 등록하면 된다.
 */
@Slf4j
@Component
public class NoOpFcmSender implements FcmSender {

    @Override
    public void send(String fcmToken, Notification notification) {
        log.debug(
                "[FCM-NOOP] token={}, type={}, title={}, body={}",
                fcmToken, notification.getType(), notification.getTitle(), notification.getBody()
        );
    }
}