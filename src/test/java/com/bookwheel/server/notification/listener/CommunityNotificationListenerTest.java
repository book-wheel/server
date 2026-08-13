package com.bookwheel.server.notification.listener;

import com.bookwheel.server.community.event.ReviewLikedEvent;
import com.bookwheel.server.notification.event.NotificationEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CommunityNotificationListenerTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CommunityNotificationListener listener;

    @Test
    @DisplayName("리뷰 공감 알림 payload에 reviewId와 ISBN을 제공한다")
    void onReviewLikedIncludesIsbnInPayload() {
        listener.onReviewLiked(new ReviewLikedEvent(
                9L,
                "9788954681179",
                "reviewerUserPK",
                "likerUserPK",
                "공감한 사용자"
        ));

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        NotificationEvent notification = captor.getValue();

        assertThat(notification.deepLink()).isEqualTo("/reviews/9");
        assertThat(notification.payload())
                .containsEntry("reviewId", 9L)
                .containsEntry("isbn", "9788954681179")
                .containsEntry("likerUserPK", "likerUserPK");
    }
}
