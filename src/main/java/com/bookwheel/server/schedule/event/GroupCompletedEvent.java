package com.bookwheel.server.schedule.event;

public record GroupCompletedEvent(
        String groupId,
        String groupName
) {
}