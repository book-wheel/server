package com.bookwheel.server.wheel.event;

public record WheelCompletedEvent(
        String wheelStateId,
        String groupId,
        String groupName,
        String completedUserPK,
        String completedNickname,
        String bookTitle
) {
}
