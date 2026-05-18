package com.bookwheel.server.notification.listener;

import com.bookwheel.server.group.dto.setting.MemberRequestStatus;
import com.bookwheel.server.group.event.GroupJoinDecidedEvent;
import com.bookwheel.server.group.event.GroupJoinRequestedEvent;
import com.bookwheel.server.member.enums.MemberRole;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.notification.enums.NotificationType;
import com.bookwheel.server.notification.event.NotificationEvent;
import com.bookwheel.server.notification.support.NotificationText;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GroupNotificationListener {

    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJoinRequested(GroupJoinRequestedEvent event) {
        String nick = NotificationText.safe(event.applicantNickname(), 30);
        String group = NotificationText.safe(event.groupName(), 30);
        memberRepository.findFirstByGroup_GroupIdAndMemberRoleAndMemberStatus(
                event.groupId(), MemberRole.LEADER, MemberStatus.ACTIVE
        ).ifPresent(leader -> eventPublisher.publishEvent(NotificationEvent.builder()
                .recipientUserId(leader.getUser().getId())
                .type(NotificationType.GROUP_JOIN_REQUESTED)
                .title("새 가입 요청")
                .body(nick + "님이 '" + group + "' 그룹에 가입을 신청했어요.")
                .deepLink("/groups/" + event.groupId() + "/members/requests")
                .payload(Map.of(
                        "groupId", event.groupId(),
                        "applicantUserId", event.applicantUserId()
                ))
                .build()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJoinDecided(GroupJoinDecidedEvent event) {
        boolean approved = event.status() == MemberRequestStatus.APPROVED;
        String group = NotificationText.safe(event.groupName(), 30);
        eventPublisher.publishEvent(NotificationEvent.builder()
                .recipientUserId(event.applicantUserId())
                .type(approved ? NotificationType.GROUP_JOIN_APPROVED : NotificationType.GROUP_JOIN_REJECTED)
                .title(approved ? "가입 승인" : "가입 거절")
                .body(approved
                        ? "'" + group + "' 그룹의 가입이 승인되었어요."
                        : "'" + group + "' 그룹의 가입이 거절되었어요.")
                .deepLink("/groups/" + event.groupId())
                .payload(Map.of(
                        "groupId", event.groupId(),
                        "status", event.status().name()
                ))
                .build());
    }
}