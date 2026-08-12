package com.bookwheel.server.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OAuth2LoginCodeExchangeRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9_-]{43}$")
        String code,

        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9\\-._~]{43,128}$")
        String codeVerifier
) {
}
