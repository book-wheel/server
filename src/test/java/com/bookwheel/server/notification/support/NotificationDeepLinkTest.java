package com.bookwheel.server.notification.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDeepLinkTest {

    @Test
    @DisplayName("게시물·리뷰 딥링크에서 대상 ID를 읽는다")
    void extractsTargetId() {
        assertThat(NotificationDeepLink.extractPostId(NotificationDeepLink.post(7L))).isEqualTo(7L);
        assertThat(NotificationDeepLink.extractReviewId(NotificationDeepLink.review(3L))).isEqualTo(3L);
    }

    @Test
    @DisplayName("다른 종류의 딥링크는 대상 ID로 인정하지 않는다")
    void doesNotExtractIdFromOtherDeepLinks() {
        assertThat(NotificationDeepLink.extractPostId("/reviews/7")).isNull();
        assertThat(NotificationDeepLink.extractReviewId("/posts/3")).isNull();
        assertThat(NotificationDeepLink.extractPostId("/groups/group-1")).isNull();
        assertThat(NotificationDeepLink.extractPostId(null)).isNull();
    }

    @Test
    @DisplayName("뒤에 경로가 더 붙거나 숫자가 아닌 링크는 대상 ID로 인정하지 않는다")
    void doesNotExtractIdFromMalformedDeepLinks() {
        // 잘못 읽으면 엉뚱한 게시물을 잠그거나 살아 있는 알림 저장을 건너뛰게 된다.
        assertThat(NotificationDeepLink.extractPostId("/posts/7/comments")).isNull();
        assertThat(NotificationDeepLink.extractPostId("/posts/abc")).isNull();
        assertThat(NotificationDeepLink.extractPostId("/posts/")).isNull();
    }
}
