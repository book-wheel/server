package com.bookwheel.server.notification.listener;

import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.notification.enums.NotificationType;
import com.bookwheel.server.notification.event.BulkNotificationEvent;
import com.bookwheel.server.notification.support.NotificationText;
import com.bookwheel.server.wheel.event.WheelCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WheelNotificationListener {

    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWheelCompleted(WheelCompletedEvent event) {
        // 완독 인증을 올린 본인을 제외한 같은 모임의 ACTIVE 멤버에게 알림을 보낸다.
        List<String> recipients = memberRepository
                .findAllWithUserByGroupIdAndStatus(event.groupId(), MemberStatus.ACTIVE)
                .stream()
                .map(m -> m.getUser().getId())
                .filter(userPK -> !userPK.equals(event.completedUserPK()))
                .toList();
        if (recipients.isEmpty()) {
            return;
        }

        String safeTitle = NotificationText.safe(event.bookTitle(), 40);
        String safeNick = NotificationText.safe(event.completedNickname(), 30);
        String bookPart = !safeTitle.isEmpty() ? "『" + safeTitle + "』" : "한 권";
        String body = safeNick + "님이 " + bookPart + " 완독 인증을 올렸어요. 한번 보러 갈까요?";

        eventPublisher.publishEvent(BulkNotificationEvent.builder()
                .recipientUserPKs(recipients)
                .type(NotificationType.WHEEL_COMPLETED_BY_PEER)
                .title("완독 인증")
                .body(body)
                .deepLink("/groups/" + event.groupId() + "/wheels/" + event.wheelStateId())
                .groupId(event.groupId())
                .payload(Map.of(
                        "groupId", event.groupId(),
                        "wheelStateId", event.wheelStateId(),
                        "completedUserPK", event.completedUserPK()
                ))
                .build());
    }
}
