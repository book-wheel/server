package com.bookwheel.server.notification.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자 설정에서 on/off할 수 있는 알림 카테고리")
public enum NotificationCategory {
    GROUP,      // 가입/멤버십
    ROUND,      // 라운드/일정/완독/순서
    COMMUNITY,  // 좋아요/댓글
    REPORT,     // 신고/제재
    ACCOUNT     // 인증/계정
}
