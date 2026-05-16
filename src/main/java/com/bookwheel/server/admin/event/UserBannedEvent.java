package com.bookwheel.server.admin.event;

import java.time.LocalDateTime;

public record UserBannedEvent(
        String userId,
        String banType,
        String reasonMessage,
        LocalDateTime releaseDate
) {
}