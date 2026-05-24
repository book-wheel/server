package com.bookwheel.server.schedule.event;

public record GroupStartedEvent(
        String groupId,
        String groupName
) {
}