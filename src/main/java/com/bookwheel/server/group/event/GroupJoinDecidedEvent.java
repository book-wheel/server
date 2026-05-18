package com.bookwheel.server.group.event;

import com.bookwheel.server.group.dto.setting.MemberRequestStatus;

public record GroupJoinDecidedEvent(
        String groupId,
        String groupName,
        String applicantUserId,
        MemberRequestStatus status
) {
}