package com.bookwheel.server.common.jwt;

import com.bookwheel.server.common.auth.AuthRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String SECRET =
            "dGVzdC1qd3Qtc2VjcmV0LWtleS1hdC1sZWFzdC0zMi1ieXRlcy1sb25n";

    private final JwtTokenProvider tokenProvider = new JwtTokenProvider(SECRET);

    @Test
    @DisplayName("토큰 용도를 Access·Onboarding과 Refresh로 구분한다")
    void distinguishesAuthenticationAndRefreshTokens() {
        String accessToken = tokenProvider.createAccessToken("user-pk", AuthRole.USER);
        String onboardingToken = tokenProvider.createOnboardingToken("user-pk");
        String refreshToken = tokenProvider.createRefreshToken("user-pk", AuthRole.USER);

        assertThat(tokenProvider.isAuthenticationToken(accessToken)).isTrue();
        assertThat(tokenProvider.isAuthenticationToken(onboardingToken)).isTrue();
        assertThat(tokenProvider.isAuthenticationToken(refreshToken)).isFalse();
        assertThat(tokenProvider.isRefreshToken(refreshToken)).isTrue();
        assertThat(tokenProvider.isRefreshToken(accessToken)).isFalse();
        assertThat(tokenProvider.isRefreshToken(onboardingToken)).isFalse();
    }
}
