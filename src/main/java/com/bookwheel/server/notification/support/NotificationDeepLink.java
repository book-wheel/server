package com.bookwheel.server.notification.support;

/**
 * 알림 딥링크 생성 규칙.
 *
 * 알림은 대상 엔티티를 외래 키가 아니라 딥링크 문자열로만 참조한다.
 * 알림을 만드는 쪽과 대상이 삭제돼 알림을 정리하는 쪽이 같은 규칙을 써야 하므로
 * 형식을 이 클래스 한 곳에서만 정의한다.
 */
public final class NotificationDeepLink {

    private static final String POST_PREFIX = "/posts/";
    private static final String REVIEW_PREFIX = "/reviews/";

    private NotificationDeepLink() {
    }

    public static String post(Long postId) {
        return POST_PREFIX + postId;
    }

    public static String review(Long reviewId) {
        return REVIEW_PREFIX + reviewId;
    }
}
