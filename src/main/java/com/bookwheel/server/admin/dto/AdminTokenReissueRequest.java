package com.bookwheel.server.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminTokenReissueRequest(
        @NotBlank
        String refreshToken
) {
}
