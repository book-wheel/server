package com.bookwheel.server.notification.service;

import com.bookwheel.server.notification.entity.Notification;
import com.bookwheel.server.notification.entity.NotificationPreference;
import com.bookwheel.server.notification.push.PushTarget;
import com.bookwheel.server.notification.repository.NotificationPreferenceRepository;
import com.bookwheel.server.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationPushTargetResolver {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;

    /**
     * 알림 저장 트랜잭션과 독립된 신규 트랜잭션에서 최신 푸시 대상을 결정한다.
     * 커밋 사이에 로그아웃하거나 같은 기기 토큰이 다른 계정으로 이전되었으면
     * 이전 사용자의 알림을 해당 토큰으로 보내지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<PushTarget> resolve(List<Long> notificationIds) {
        if (notificationIds == null || notificationIds.isEmpty()) {
            return List.of();
        }

        List<Notification> notifications = notificationRepository.findAllById(notificationIds);
        if (notifications.isEmpty()) {
            return List.of();
        }

        Collection<String> recipientUserPKs = notifications.stream()
                .map(Notification::getRecipientUserPK)
                .distinct()
                .toList();
        Map<String, NotificationPreference> preferencesByUserPK = new LinkedHashMap<>();
        for (NotificationPreference preference : preferenceRepository.findAllByUserPKIn(recipientUserPKs)) {
            preferencesByUserPK.put(preference.getUserPK(), preference);
        }

        return notifications.stream()
                .map(notification -> toPushTarget(notification, preferencesByUserPK))
                .filter(target -> target != null)
                .toList();
    }

    private PushTarget toPushTarget(
            Notification notification,
            Map<String, NotificationPreference> preferencesByUserPK
    ) {
        NotificationPreference preference = preferencesByUserPK.get(notification.getRecipientUserPK());
        if (preference == null || !preference.allowsPush(notification.getCategory())) {
            return null;
        }

        String expoPushToken = preference.getExpoPushToken();
        if (expoPushToken == null || expoPushToken.isBlank()) {
            return null;
        }
        return new PushTarget(expoPushToken, notification);
    }
}
