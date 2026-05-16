package com.bookwheel.server.notification.push;

import com.bookwheel.server.notification.entity.Notification;

/**
 * FCM 등 외부 푸시 서비스 어댑터 인터페이스.
 * 실제 어댑터는 추후 구현하고, 현재는 NoOp 구현이 기본 등록된다.
 */
public interface FcmSender {

    /**
     * @param fcmToken      대상 디바이스 토큰 (없으면 호출되지 않음)
     * @param notification  영속화된 알림 엔티티
     */
    void send(String fcmToken, Notification notification);
}