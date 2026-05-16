package com.bookwheel.server.order.event;

import java.util.List;

public record ReadOrderAssignedEvent(
        String groupId,
        String groupName,
        List<String> orderedUserIds
) {
}