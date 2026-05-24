package com.bookwheel.server.admin.event;

import java.time.LocalDateTime;

public record UserBannedEvent(
        String userPK,
        String banType,
        String reasonMessage,
        LocalDateTime releaseDate
) {
}
