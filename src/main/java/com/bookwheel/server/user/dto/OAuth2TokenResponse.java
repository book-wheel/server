package com.bookwheel.server.user.dto;

public record OAuth2TokenResponse(
        String accessToken,
        String refreshToken,
        boolean isFirstLogin
) {
}
