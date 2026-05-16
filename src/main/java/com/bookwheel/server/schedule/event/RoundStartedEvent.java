package com.bookwheel.server.schedule.event;

public record RoundStartedEvent(
        String groupId,
        String groupName,
        Integer roundNumber
) {
}