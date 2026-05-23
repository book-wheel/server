package com.bookwheel.server.notification.listener;

import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.notification.enums.NotificationType;
import com.bookwheel.server.notification.event.BulkNotificationEvent;
import com.bookwheel.server.notification.support.NotificationText;
import com.bookwheel.server.schedule.event.GroupCompletedEvent;
import com.bookwheel.server.schedule.event.GroupStartedEvent;
import com.bookwheel.server.schedule.event.RoundDeadlineApproachingEvent;
import com.bookwheel.server.schedule.event.RoundFinishedUnfinishedEvent;
import com.bookwheel.server.schedule.event.RoundStartedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RoundNotificationListener {

    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGroupStarted(GroupStartedEvent event) {
        String group = NotificationText.safe(event.groupName(), 30);
        notifyAllActiveMembers(event.groupId(),
                NotificationType.GROUP_STARTED,
                "독서 그룹 시작",
                "오늘부터 '" + group + "' 그룹의 책바퀴가 시작돼요!",
                Map.of("groupId", event.groupId()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoundStarted(RoundStartedEvent event) {
        String group = NotificationText.safe(event.groupName(), 30);
        notifyAllActiveMembers(event.groupId(),
                NotificationType.ROUND_STARTED,
                event.roundNumber() + "라운드 시작",
                "'" + group + "' " + event.roundNumber() + "라운드가 시작되었어요. 이번에 읽을 책을 확인해보세요.",
                Map.of("groupId", event.groupId(), "roundNumber", event.roundNumber()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoundFinishedUnfinished(RoundFinishedUnfinishedEvent event) {
        String group = NotificationText.safe(event.groupName(), 30);
        notifyAllActiveMembers(event.groupId(),
                NotificationType.ROUND_FINISHED_UNFINISHED,
                event.roundNumber() + "라운드 종료",
                "'" + group + "' " + event.roundNumber() + "라운드가 종료되었어요. 미완독은 미완독으로 처리됩니다.",
                Map.of("groupId", event.groupId(), "roundNumber", event.roundNumber()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGroupCompleted(GroupCompletedEvent event) {
        String group = NotificationText.safe(event.groupName(), 30);
        notifyAllActiveMembers(event.groupId(),
                NotificationType.GROUP_COMPLETED,
                "그룹 종료",
                "'" + group + "' 그룹의 모든 책바퀴가 끝났어요. 수고하셨습니다!",
                Map.of("groupId", event.groupId()));
    }

    /**
     * 리마인더는 스케줄러가 직접 EventListener 로 발행하므로 트랜잭션 이벤트가 아닌 일반 EventListener.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    @EventListener
    public void onDeadlineApproaching(RoundDeadlineApproachingEvent event) {
        String group = NotificationText.safe(event.groupName(), 30);
        String body = "'" + group + "' " + event.roundNumber() + "라운드 종료까지 D-" + event.daysLeft() + "예요. 완독 인증을 잊지 마세요!";
        notifyAllActiveMembers(event.groupId(),
                NotificationType.ROUND_DEADLINE_APPROACHING,
                "라운드 마감 D-" + event.daysLeft(),
                body,
                Map.of(
                        "groupId", event.groupId(),
                        "roundNumber", event.roundNumber(),
                        "daysLeft", event.daysLeft()
                ));
    }

    private void notifyAllActiveMembers(
            String groupId,
            NotificationType type,
            String title,
            String body,
            Map<String, Object> payload
    ) {
        List<String> recipients = memberRepository
                .findAllWithUserByGroupIdAndStatus(groupId, MemberStatus.ACTIVE)
                .stream()
                .map(m -> m.getUser().getId())
                .toList();
        if (recipients.isEmpty()) {
            return;
        }
        eventPublisher.publishEvent(BulkNotificationEvent.builder()
                .recipientUserPKs(recipients)
                .type(type)
                .title(title)
                .body(body)
                .deepLink("/groups/" + groupId)
                .payload(payload)
                .build());
    }
}
