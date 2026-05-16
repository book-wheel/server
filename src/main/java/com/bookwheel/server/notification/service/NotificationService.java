package com.bookwheel.server.notification.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.notification.dto.NotificationResponse;
import com.bookwheel.server.notification.dto.UnreadCountResponse;
import com.bookwheel.server.notification.entity.Notification;
import com.bookwheel.server.notification.entity.NotificationPreference;
import com.bookwheel.server.notification.enums.NotificationCategory;
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
        if (event.recipientUserId() == null || event.recipientUserId().isBlank()) {
            return null;
        }

        NotificationCategory category = event.type().getCategory();
        NotificationPreference preference = preferenceService.getOrInit(event.recipientUserId());
        if (!preference.allows(category)) {
            return null;
        }

        Notification notification = Notification.builder()
                .recipientUserId(event.recipientUserId())
                .type(event.type())
                .category(category)
                .title(event.title())
                .body(event.body())
                .deepLink(event.deepLink())
                .payload(serializePayload(event.payload()))
                .build();

        Notification saved = notificationRepository.save(notification);

        // FCM 푸시 - 푸시 끄지 않았고 토큰이 있을 때만
        if (Boolean.TRUE.equals(preference.getPushEnabled()) && preference.getFcmToken() != null
                && !preference.getFcmToken().isBlank()) {
            try {
                fcmSender.send(preference.getFcmToken(), saved);
            } catch (Exception e) {
                log.warn("FCM 발송 실패: notificationId={}, reason={}", saved.getId(), e.getMessage());
            }
        }
        return saved;
    }

    public Page<NotificationResponse> list(String userId, Pageable pageable) {
        return notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationResponse::from);
    }

    public UnreadCountResponse unreadCount(String userId) {
        return new UnreadCountResponse(
                notificationRepository.countByRecipientUserIdAndIsReadFalse(userId)
        );
    }

    @Transactional
    public void markRead(String userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (!notification.getRecipientUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_FORBIDDEN);
        }
        notification.markRead();
    }

    @Transactional
    public int markAllRead(String userId) {
        return notificationRepository.markAllRead(userId);
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