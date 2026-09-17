package com.bookwheel.server.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "일반 회원 토큰 재발급 결과")
public record TokenResponse(
        @Schema(description = "새로 발급된 일반 Access Token")
        String accessToken,
        @Schema(description = "기존 Refresh Token")
        String refreshToken
) {}
