package com.bookwheel.server.chat.dto;

import lombok.Builder;

@Builder
public record ChatRoomReadResponse(
        String chatRoomId,
        Long lastReadMessageId
) {
}
