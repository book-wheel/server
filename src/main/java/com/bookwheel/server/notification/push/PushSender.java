package com.bookwheel.server.notification.push;

import com.bookwheel.server.notification.entity.Notification;

import java.util.List;

/**
 * 저장된 알림을 푸시 제공자에 전송한다.
 */
public interface PushSender {

    void send(String expoPushToken, Notification notification);

    /**
     * 수신자별 데이터가 유지되도록 각 토큰과 알림을 짝지어 일괄 전송한다.
     */
    void sendBatch(List<PushTarget> targets);
}
