package com.bookwheel.server.chat.dto;

import com.bookwheel.server.chat.entity.ChatMessageType;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ChatMessageResponse(
        Long messageId,
        ChatMessageSenderResponse sender,
        ChatMessageType type,
        String content,
        String imageKey,
        String imageUrl,
        LocalDateTime createdAt
) {
}
