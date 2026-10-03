package com.bookwheel.server.common.oauth2.apple;

import com.bookwheel.server.common.oauth2.SocialLoginPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class AppleOAuth2AuthorizedClientRepositoryTest {

    private final AppleOAuthCredentialService credentialService = mock(AppleOAuthCredentialService.class);
    private final AppleOAuth2AuthorizedClientRepository repository =
            new AppleOAuth2AuthorizedClientRepository(credentialService);

    @Test
    @DisplayName("Apple 로그인 응답의 refresh token을 userPK에 연결해 저장한다")
    void storesAppleRefreshToken() {
        Authentication authentication = authentication("user-pk");
        OAuth2AuthorizedClient client = authorizedClient("apple", "refresh-token");

        repository.saveAuthorizedClient(client, authentication, null, null);

        then(credentialService).should().store("user-pk", "refresh-token");
    }

    @Test
    @DisplayName("Apple token 응답에 refresh token이 없으면 인증을 실패시킨다")
    void rejectsAppleResponseWithoutRefreshToken() {
        Authentication authentication = authentication("user-pk");
        OAuth2AuthorizedClient client = authorizedClient("apple", null);

        assertThatThrownBy(() -> repository.saveAuthorizedClient(client, authentication, null, null))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("apple_refresh_token_missing");
    }

    @Test
    @DisplayName("Apple 외 provider token은 별도 저장하지 않는다")
    void ignoresOtherProviders() {
        repository.saveAuthorizedClient(
                authorizedClient("google", "refresh-token"),
                authentication("user-pk"),
                null,
                null
        );

        then(credentialService).should(never()).store("user-pk", "refresh-token");
    }

    private Authentication authentication(String userPK) {
        SocialLoginPrincipal principal = mock(SocialLoginPrincipal.class);
        given(principal.getUserPK()).willReturn(userPK);
        Authentication authentication = mock(Authentication.class);
        given(authentication.getPrincipal()).willReturn(principal);
        return authentication;
    }

    private OAuth2AuthorizedClient authorizedClient(String registrationId, String refreshToken) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(registrationId)
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://example.com/callback")
                .authorizationUri("https://example.com/authorize")
                .tokenUri("https://example.com/token")
                .build();
        Instant issuedAt = Instant.parse("2026-09-17T01:00:00Z");
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "access-token",
                issuedAt,
                issuedAt.plusSeconds(300),
                Set.of("openid")
        );
        OAuth2RefreshToken token = refreshToken == null
                ? null
                : new OAuth2RefreshToken(refreshToken, issuedAt);
        return new OAuth2AuthorizedClient(registration, "user-pk", accessToken, token);
    }
}
