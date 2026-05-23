package com.bookwheel.server.notification.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.notification.dto.NotificationResponse;
import com.bookwheel.server.notification.dto.UnreadCountResponse;
import com.bookwheel.server.notification.entity.Notification;
import com.bookwheel.server.notification.entity.NotificationPreference;
import com.bookwheel.server.notification.enums.NotificationCategory;
import com.bookwheel.server.notification.event.BulkNotificationEvent;
import com.bookwheel.server.notification.event.NotificationEvent;
import com.bookwheel.server.notification.push.FcmSender;
import com.bookwheel.server.notification.repository.NotificationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceService preferenceService;
    private final FcmSender fcmSender;
    private final ObjectMapper objectMapper;

    /**
     * 단건 알림 생성. 카테고리 off 인 사용자에게는 인앱/푸시 모두 보내지 않는다.
     * @return 영속화된 알림 (off 로 인해 스킵 시 null)
     */
    @Transactional
    public Notification create(NotificationEvent event) {
        if (event.recipientUserPK() == null || event.recipientUserPK().isBlank()) {
            return null;
        }

        NotificationCategory category = event.type().getCategory();
        NotificationPreference preference = preferenceService.getOrInit(event.recipientUserPK());
        if (!preference.allows(category)) {
            return null;
        }

        Notification notification = Notification.builder()
                .recipientUserPK(event.recipientUserPK())
                .type(event.type())
                .category(category)
                .title(event.title())
                .body(event.body())
                .deepLink(event.deepLink())
                .payload(serializePayload(event.payload()))
                .build();

        Notification saved = notificationRepository.save(notification);

        // FCM 푸시 - 카테고리별 푸시 허용 여부 + FCM 토큰 보유 시 발송
        // (REPORT/ACCOUNT 는 pushEnabled 설정 무시하고 강제 발송)
        if (preference.allowsPush(category) && preference.getFcmToken() != null
                && !preference.getFcmToken().isBlank()) {
            try {
                fcmSender.send(preference.getFcmToken(), saved);
            } catch (Exception e) {
                log.warn("FCM 발송 실패: notificationId={}, reason={}", saved.getId(), e.getMessage());
            }
        }
        return saved;
    }

    /**
     * 동일 title/body 의 알림을 다수 수신자에게 일괄 생성한다.
     * - preference 일괄 조회로 N+1 SELECT 제거
     * - notification saveAll 로 단일 트랜잭션에서 일괄 영속화
     * - 푸시 토큰을 모아 한 번의 multicast 호출로 전송
     *
     * @return 실제로 영속화된 알림 (카테고리 off 사용자는 제외)
     */
    @Transactional
    public List<Notification> createBulk(BulkNotificationEvent event) {
        if (event.recipientUserPKs() == null || event.recipientUserPKs().isEmpty()) {
            return List.of();
        }

        List<String> recipients = new ArrayList<>(new LinkedHashSet<>(
                event.recipientUserPKs().stream()
                        .filter(pk -> pk != null && !pk.isBlank())
                        .toList()
        ));
        if (recipients.isEmpty()) {
            return List.of();
        }

        NotificationCategory category = event.type().getCategory();
        Map<String, NotificationPreference> preferences = preferenceService.getOrInitAll(recipients);
        String serializedPayload = serializePayload(event.payload());

        List<Notification> toInsert = new ArrayList<>(recipients.size());
        for (String pk : recipients) {
            NotificationPreference preference = preferences.get(pk);
            if (preference == null || !preference.allows(category)) {
                continue;
            }
            toInsert.add(Notification.builder()
                    .recipientUserPK(pk)
                    .type(event.type())
                    .category(category)
                    .title(event.title())
                    .body(event.body())
                    .deepLink(event.deepLink())
                    .payload(serializedPayload)
                    .build());
        }
        if (toInsert.isEmpty()) {
            return List.of();
        }

        List<Notification> saved = notificationRepository.saveAll(toInsert);

        List<String> tokens = new ArrayList<>(saved.size());
        for (Notification n : saved) {
            NotificationPreference preference = preferences.get(n.getRecipientUserPK());
            if (preference == null || !preference.allowsPush(category)) {
                continue;
            }
            String token = preference.getFcmToken();
            if (token == null || token.isBlank()) {
                continue;
            }
            tokens.add(token);
        }
        if (!tokens.isEmpty()) {
            try {
                fcmSender.sendMulticast(tokens, saved.get(0));
            } catch (Exception e) {
                log.warn("FCM 멀티캐스트 발송 실패: type={}, count={}, reason={}",
                        event.type(), tokens.size(), e.getMessage());
            }
        }
        return saved;
    }

    public Page<NotificationResponse> list(String userPK, Pageable pageable) {
        return notificationRepository.findByRecipientUserPKOrderByCreatedAtDesc(userPK, pageable)
                .map(NotificationResponse::from);
    }

    public UnreadCountResponse unreadCount(String userPK) {
        return new UnreadCountResponse(
                notificationRepository.countByRecipientUserPKAndIsReadFalse(userPK)
        );
    }

    @Transactional
    public void markRead(String userPK, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (!notification.getRecipientUserPK().equals(userPK)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_FORBIDDEN);
        }
        notification.markRead();
    }

    @Transactional
    public int markAllRead(String userPK) {
        return notificationRepository.markAllRead(userPK);
    }

    private String serializePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("알림 payload 직렬화 실패: {}", e.getMessage());
            return null;
        }
    }
}
