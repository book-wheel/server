package com.bookwheel.server.user.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenReissueRequest(
        @NotBlank
        String refreshToken
) {}