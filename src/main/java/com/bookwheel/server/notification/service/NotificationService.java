package com.bookwheel.server.notification.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.repository.BookReviewRepository;
import com.bookwheel.server.community.repository.PostRepository;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.notification.dto.NotificationResponse;
import com.bookwheel.server.notification.dto.UnreadCountResponse;
import com.bookwheel.server.notification.entity.Notification;
import com.bookwheel.server.notification.entity.NotificationPreference;
import com.bookwheel.server.notification.enums.NotificationCategory;
import com.bookwheel.server.notification.event.BulkNotificationEvent;
import com.bookwheel.server.notification.event.NotificationEvent;
import com.bookwheel.server.notification.push.FcmSender;
import com.bookwheel.server.notification.repository.NotificationRepository;
import com.bookwheel.server.notification.support.NotificationDeepLink;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
    private final GroupRepository groupRepository;
    private final PostRepository postRepository;
    private final BookReviewRepository bookReviewRepository;
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

        if (!lockGroupIfPresent(event.groupId())) {
            return null;
        }

        if (!lockDeepLinkTargetIfPresent(event.deepLink())) {
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
                .groupId(event.groupId())
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

        if (!lockGroupIfPresent(event.groupId())) {
            return List.of();
        }

        if (!lockDeepLinkTargetIfPresent(event.deepLink())) {
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
                    .groupId(event.groupId())
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

    @Transactional
    public void deleteByGroupId(String groupId) {
        // 새 group_id와 기존 payload 기반 알림을 함께 정리해 삭제 범위를 누락하지 않는다.
        notificationRepository.deleteAllByGroupId(groupId);

        // 스키마 변경 전 payload만으로 저장된 알림도 정확히 같은 groupId인 경우 함께 제거한다.
        notificationRepository.findByGroupIdIsNull().stream()
                .filter(notification -> groupId.equals(extractGroupId(notification.getPayload())))
                .forEach(notificationRepository::delete);
    }

    @Transactional
    public void deleteByPostId(Long postId) {
        // 게시물이 삭제되면 해당 게시물로 이동하는 알림은 열 수 없는 링크가 되므로 함께 정리한다.
        notificationRepository.deleteAllByDeepLink(NotificationDeepLink.post(postId));
    }

    @Transactional
    public void deleteByReviewId(Long reviewId) {
        // 리뷰가 삭제되면 해당 리뷰로 이동하는 알림은 열 수 없는 링크가 되므로 함께 정리한다.
        notificationRepository.deleteAllByDeepLink(NotificationDeepLink.review(reviewId));
    }

    @Transactional
    public int backfillGroupIds() {
        // 기존 알림 중 실제로 존재하는 모임을 가리키는 행만 안전하게 새 컬럼에 채운다.
        int updated = 0;
        for (Notification notification : notificationRepository.findByGroupIdIsNull()) {
            String groupId = extractGroupId(notification.getPayload());
            if (groupId == null || groupRepository.findByGroupIdForUpdate(groupId).isEmpty()) {
                continue;
            }
            notification.assignGroupId(groupId);
            updated++;
        }
        return updated;
    }

    private boolean lockGroupIfPresent(String groupId) {
        // 일반 계정 알림은 잠그지 않고, 모임 알림만 삭제와 같은 행 잠금으로 직렬화한다.
        if (groupId == null || groupId.isBlank()) {
            return true;
        }

        // 삭제 트랜잭션과 같은 그룹 행을 잠가 삭제 전 저장은 정리되고 삭제 후 저장은 건너뛰게 한다.
        // 삭제가 완료된 모임의 비동기 알림은 보존할 운영 데이터가 아니므로 저장하지 않는다.
        return groupRepository.findByGroupIdForUpdate(groupId)
                .filter(group -> group.getGroupState() != State.DELETED)
                .isPresent();
    }

    /**
     * 딥링크가 게시물·리뷰를 가리키면 대상 행을 삭제와 같은 기준으로 잠그고 아직 살아 있는지 확인한다.
     *
     * 알림 저장은 좋아요·댓글 트랜잭션이 커밋된 뒤 비동기로 실행되므로, 그 사이에 대상이 삭제되면
     * 삭제 트랜잭션의 알림 정리는 이미 끝난 뒤라 열 수 없는 링크가 그대로 남는다.
     * 삭제 쪽이 대상 행을 먼저 잠그기 때문에 여기서 같은 행을 잠그면
     * 삭제 전 저장은 삭제 트랜잭션이 정리하고, 삭제 후 저장은 대상이 없어 건너뛰게 된다.
     *
     * @return 저장을 계속해도 되면 true, 대상이 이미 삭제됐으면 false
     */
    private boolean lockDeepLinkTargetIfPresent(String deepLink) {
        Long postId = NotificationDeepLink.extractPostId(deepLink);
        if (postId != null) {
            return postRepository.findByPostIdForUpdate(postId).isPresent();
        }

        Long reviewId = NotificationDeepLink.extractReviewId(deepLink);
        if (reviewId != null) {
            return bookReviewRepository.findByReviewIdForUpdate(reviewId).isPresent();
        }

        // 게시물·리뷰가 아닌 딥링크는 정리 대상이 아니므로 잠그지 않는다.
        return true;
    }

    private String extractGroupId(String payload) {
        // deep link가 아닌 payload의 정확한 문자열 필드만 그룹 식별자로 인정한다.
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            JsonNode payloadNode = objectMapper.readTree(payload);
            if (payloadNode == null) {
                return null;
            }
            JsonNode groupIdNode = payloadNode.get("groupId");
            return groupIdNode != null && groupIdNode.isTextual() && !groupIdNode.asText().isBlank()
                    ? groupIdNode.asText()
                    : null;
        } catch (JsonProcessingException exception) {
            log.warn("기존 알림 groupId 해석 실패: reason={}", exception.getMessage());
            return null;
        }
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
