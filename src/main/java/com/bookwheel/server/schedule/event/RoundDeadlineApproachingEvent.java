package com.bookwheel.server.schedule.event;

public record RoundDeadlineApproachingEvent(
        String groupId,
        String groupName,
        Integer roundNumber,
        int daysLeft
) {
}