package com.bookwheel.server.chat.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record ChatMessageListResponse(
        List<ChatMessageResponse> messages,
        Long nextCursor,
        boolean hasNext
) {
}
