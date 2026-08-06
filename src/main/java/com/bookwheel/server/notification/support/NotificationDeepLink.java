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

    /**
     * 딥링크가 게시물을 가리키면 게시물 ID를, 아니면 null 을 반환한다.
     * 알림을 저장하기 전에 대상이 아직 살아 있는지 확인하는 용도다.
     */
    public static Long extractPostId(String deepLink) {
        return extractId(deepLink, POST_PREFIX);
    }

    // 딥링크가 리뷰를 가리키면 리뷰 ID를, 아니면 null 을 반환한다.
    public static Long extractReviewId(String deepLink) {
        return extractId(deepLink, REVIEW_PREFIX);
    }

    private static Long extractId(String deepLink, String prefix) {
        if (deepLink == null || !deepLink.startsWith(prefix)) {
            return null;
        }

        // "/posts/1/comments" 처럼 뒤에 경로가 더 붙은 링크는 대상이 다르므로 인정하지 않는다.
        String id = deepLink.substring(prefix.length());
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
