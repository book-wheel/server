package com.bookwheel.server.admin.dto;

import lombok.Builder;

@Builder
public record AdminTokenResponse(
        String accessToken,
        String refreshToken
) {
}
