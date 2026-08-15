package com.bookwheel.server.notification.service;

import com.bookwheel.server.notification.entity.Notification;
import com.bookwheel.server.notification.entity.NotificationPreference;
import com.bookwheel.server.notification.enums.NotificationType;
import com.bookwheel.server.notification.push.PushTarget;
import com.bookwheel.server.notification.repository.NotificationPreferenceRepository;
import com.bookwheel.server.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class NotificationPushTargetResolverTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @InjectMocks
    private NotificationPushTargetResolver targetResolver;

    @Test
    @DisplayName("알림 저장 후 다시 조회한 현재 수신자 토큰으로 푸시 대상을 만든다")
    void resolveUsesCurrentRecipientToken() {
        Notification notification = notification(41L, "recipientUserPK");
        NotificationPreference preference = NotificationPreference.builder()
                .userPK("recipientUserPK")
                .expoPushToken("ExpoPushToken[current_token]")
                .build();
        given(notificationRepository.findAllById(List.of(41L))).willReturn(List.of(notification));
        given(preferenceRepository.findAllByUserPKIn(List.of("recipientUserPK")))
                .willReturn(List.of(preference));

        List<PushTarget> targets = targetResolver.resolve(List.of(41L));

        assertThat(targets).containsExactly(new PushTarget("ExpoPushToken[current_token]", notification));
    }

    @Test
    @DisplayName("알림 저장 후 로그아웃하거나 토큰 소유자가 바뀌면 이전 사용자에게 푸시를 보내지 않는다")
    void resolveSkipsPreviousUserAfterTokenOwnershipChanges() {
        Notification notification = notification(41L, "previousUserPK");
        given(notificationRepository.findAllById(List.of(41L))).willReturn(List.of(notification));
        given(preferenceRepository.findAllByUserPKIn(List.of("previousUserPK")))
                .willReturn(List.of(NotificationPreference.defaultsFor("previousUserPK")));

        List<PushTarget> targets = targetResolver.resolve(List.of(41L));

        assertThat(targets).isEmpty();
        then(preferenceRepository).should().findAllByUserPKIn(List.of("previousUserPK"));
    }

    @Test
    @DisplayName("커뮤니티 푸시를 끄면 토큰이 있어도 발송 대상에서 제외한다")
    void resolveRespectsLatestPushPreference() {
        Notification notification = notification(41L, "recipientUserPK");
        NotificationPreference preference = NotificationPreference.builder()
                .userPK("recipientUserPK")
                .communityEnabled(true)
                .pushEnabled(false)
                .expoPushToken("ExpoPushToken[current_token]")
                .build();
        given(notificationRepository.findAllById(List.of(41L))).willReturn(List.of(notification));
        given(preferenceRepository.findAllByUserPKIn(List.of("recipientUserPK")))
                .willReturn(List.of(preference));

        assertThat(targetResolver.resolve(List.of(41L))).isEmpty();
    }

    private Notification notification(Long id, String recipientUserPK) {
        return Notification.builder()
                .id(id)
                .recipientUserPK(recipientUserPK)
                .type(NotificationType.REVIEW_LIKED)
                .category(NotificationType.REVIEW_LIKED.getCategory())
                .title("리뷰 공감")
                .body("회원님의 리뷰에 공감했어요.")
                .deepLink("/reviews/3")
                .build();
    }
}
