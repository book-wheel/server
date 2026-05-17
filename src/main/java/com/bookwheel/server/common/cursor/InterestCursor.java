package com.bookwheel.server.common.cursor;

import java.time.LocalDateTime;

public record InterestCursor(
    LocalDateTime interestedAt,
    Long bookId
) {
}
