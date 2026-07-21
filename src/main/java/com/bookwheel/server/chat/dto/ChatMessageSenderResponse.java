package com.bookwheel.server.chat.dto;

import lombok.Builder;

@Builder
public record ChatMessageSenderResponse(
        String userPK,
        String nickname,
        String profileImageUrl
) {
}
