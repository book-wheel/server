package com.bookwheel.server.notification.service;

import com.bookwheel.server.notification.entity.Notification;
import com.bookwheel.server.notification.enums.NotificationType;
import com.bookwheel.server.notification.push.PushSender;
import com.bookwheel.server.notification.push.PushTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class NotificationPushServiceTest {

    @Mock
    private NotificationPushTargetResolver targetResolver;

    @Mock
    private PushSender pushSender;

    @InjectMocks
    private NotificationPushService notificationPushService;

    @Test
    @DisplayName("별도 발송 경로에서 최신 토큰으로 해석된 대상을 일괄 전송한다")
    void sendResolvesCurrentTargetsBeforeSending() {
        Notification notification = Notification.builder()
                .id(41L)
                .recipientUserPK("recipientUserPK")
                .type(NotificationType.REVIEW_LIKED)
                .category(NotificationType.REVIEW_LIKED.getCategory())
                .title("리뷰 공감")
                .body("회원님의 리뷰에 공감했어요.")
                .build();
        List<PushTarget> targets = List.of(new PushTarget(
                "ExpoPushToken[current_token]",
                notification
        ));
        given(targetResolver.resolve(List.of(41L))).willReturn(targets);

        notificationPushService.send(List.of(41L));

        then(pushSender).should().sendBatch(targets);
    }
}
