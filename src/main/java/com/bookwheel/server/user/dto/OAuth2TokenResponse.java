package com.bookwheel.server.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "소셜 로그인 일회용 코드 교환 결과")
public record OAuth2TokenResponse(
        @Schema(description = "isFirstLogin=true면 프로필 설정 전용 온보딩 토큰, false면 일반 Access Token")
        String accessToken,
        @Schema(description = "최초 소셜 가입자에게는 발급하지 않아 null", nullable = true)
        String refreshToken,
        @Schema(description = "true이면 약관 동의와 프로필 설정이 필요", example = "true")
        boolean isFirstLogin
) {
}
