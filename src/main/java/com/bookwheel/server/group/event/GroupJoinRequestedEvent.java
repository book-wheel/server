package com.bookwheel.server.group.event;

public record GroupJoinRequestedEvent(
        String groupId,
        String groupName,
        String applicantUserId,
        String applicantNickname
) {
}