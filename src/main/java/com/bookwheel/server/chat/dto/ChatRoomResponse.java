package com.bookwheel.server.chat.dto;

import lombok.Builder;

@Builder
public record ChatRoomResponse(
        String chatRoomId,
        String groupId,
        Long lastReadMessageId,
        long unreadCount
) {
}
