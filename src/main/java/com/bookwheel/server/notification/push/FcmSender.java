package com.bookwheel.server.notification.push;

import com.bookwheel.server.notification.entity.Notification;

import java.util.List;

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

    /**
     * 동일 title/body 를 다수 디바이스에 일괄 발송한다.
     * 실제 FCM 구현체는 MulticastMessage 로 단일 HTTP 호출로 처리해야 한다.
     *
     * @param fcmTokens 비어있지 않은 디바이스 토큰 목록
     * @param representative 알림 메타데이터 (title/body/deepLink/payload 등 공통값)
     */
    void sendMulticast(List<String> fcmTokens, Notification representative);
}