package com.bookwheel.server.schedule.event;

public record RoundFinishedUnfinishedEvent(
        String groupId,
        String groupName,
        Integer roundNumber
) {
}