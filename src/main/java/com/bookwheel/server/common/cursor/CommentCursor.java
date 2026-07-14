package com.bookwheel.server.common.cursor;

import java.time.LocalDateTime;

public record CommentCursor(
    LocalDateTime createdAt,
    Long commentId
) {
}
