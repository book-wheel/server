package com.bookwheel.server.common.cursor;

import java.time.LocalDateTime;

public record GalleryCursor(
    LocalDateTime createdAt,
    Long galleryId
) {
}
