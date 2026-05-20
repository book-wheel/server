package com.bookwheel.server.notification.listener;

import com.bookwheel.server.community.event.PostCommentedEvent;
import com.bookwheel.server.community.event.PostLikedEvent;
import com.bookwheel.server.community.event.ReviewLikedEvent;
import com.bookwheel.server.notification.enums.NotificationType;
import com.bookwheel.server.notification.event.NotificationEvent;
import com.bookwheel.server.notification.support.NotificationText;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CommunityNotificationListener {

    private final ApplicationEventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostLiked(PostLikedEvent event) {
        String nick = NotificationText.safe(event.likerNickname(), 30);
        eventPublisher.publishEvent(NotificationEvent.builder()
                .recipientUserPK(event.postOwnerUserPK())
                .type(NotificationType.POST_LIKED)
                .title("게시물 좋아요")
                .body(nick + "님이 회원님의 게시물에 좋아요를 눌렀어요.")
                .deepLink("/posts/" + event.postId())
                .payload(Map.of("postId", event.postId(), "likerUserPK", event.likerUserPK()))
                .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostCommented(PostCommentedEvent event) {
        String nick = NotificationText.safe(event.commenterNickname(), 30);
        String preview = event.commentPreview() == null ? null : NotificationText.safe(event.commentPreview(), 80);
        Map<String, Object> payload = new HashMap<>();
        payload.put("postId", event.postId());
        payload.put("commenterUserPK", event.commenterUserPK());
        if (preview != null && !preview.isEmpty()) {
            payload.put("commentPreview", preview);
        }
        eventPublisher.publishEvent(NotificationEvent.builder()
                .recipientUserPK(event.postOwnerUserPK())
                .type(NotificationType.POST_COMMENTED)
                .title("게시물 댓글")
                .body(nick + "님이 댓글을 남겼어요"
                        + (preview != null && !preview.isEmpty() ? ": " + preview : "."))
                .deepLink("/posts/" + event.postId())
                .payload(payload)
                .build());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewLiked(ReviewLikedEvent event) {
        String nick = NotificationText.safe(event.likerNickname(), 30);
        eventPublisher.publishEvent(NotificationEvent.builder()
                .recipientUserPK(event.reviewerUserPK())
                .type(NotificationType.REVIEW_LIKED)
                .title("리뷰 공감")
                .body(nick + "님이 회원님의 리뷰에 공감했어요.")
                .deepLink("/reviews/" + event.reviewId())
                .payload(Map.of("reviewId", event.reviewId(), "likerUserPK", event.likerUserPK()))
                .build());
    }
}
