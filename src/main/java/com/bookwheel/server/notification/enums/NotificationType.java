package com.bookwheel.server.notification.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
    // 그룹 가입/멤버십
    GROUP_JOIN_REQUESTED(NotificationCategory.GROUP),
    GROUP_JOIN_APPROVED(NotificationCategory.GROUP),
    GROUP_JOIN_REJECTED(NotificationCategory.GROUP),

    // 라운드/일정
    GROUP_STARTED(NotificationCategory.ROUND),
    ROUND_STARTED(NotificationCategory.ROUND),
    ROUND_DEADLINE_APPROACHING(NotificationCategory.ROUND),
    ROUND_FINISHED_UNFINISHED(NotificationCategory.ROUND),
    GROUP_COMPLETED(NotificationCategory.ROUND),

    // 완독/순서
    WHEEL_COMPLETED_BY_PEER(NotificationCategory.ROUND),
    READ_ORDER_ASSIGNED(NotificationCategory.ROUND),

    // 커뮤니티
    POST_LIKED(NotificationCategory.COMMUNITY),
    POST_COMMENTED(NotificationCategory.COMMUNITY),
    REVIEW_LIKED(NotificationCategory.COMMUNITY),

    // 신고/제재
    USER_BANNED(NotificationCategory.REPORT),

    // 계정
    ACCOUNT_DEACTIVATED(NotificationCategory.ACCOUNT);

    private final NotificationCategory category;
}
