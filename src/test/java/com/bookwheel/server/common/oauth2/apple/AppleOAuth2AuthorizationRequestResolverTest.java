package com.bookwheel.server.common.oauth2.apple;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.assertj.core.api.Assertions.assertThat;

class AppleOAuth2AuthorizationRequestResolverTest {

    @Test
    @DisplayName("Apple 인증 요청은 form_post response mode를 사용한다")
    void addsFormPostResponseModeForApple() {
        AppleOAuth2AuthorizationRequestResolver resolver = resolver(
                clientRegistration("apple", "https://appleid.apple.com/auth/authorize")
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/oauth2/authorization/apple");

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request);

        assertThat(authorizationRequest).isNotNull();
        assertThat(authorizationRequest.getAdditionalParameters())
                .containsEntry("response_mode", "form_post")
                .containsKey("nonce");
        assertThat(authorizationRequest.getState()).isNotBlank();
    }

    @Test
    @DisplayName("Apple이 아닌 provider의 인증 요청은 response mode를 변경하지 않는다")
    void leavesOtherProvidersUnchanged() {
        AppleOAuth2AuthorizationRequestResolver resolver = resolver(
                clientRegistration("google", "https://accounts.google.com/o/oauth2/v2/auth")
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/oauth2/authorization/google");

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request);

        assertThat(authorizationRequest).isNotNull();
        assertThat(authorizationRequest.getAdditionalParameters())
                .doesNotContainKey("response_mode");
    }

    private AppleOAuth2AuthorizationRequestResolver resolver(ClientRegistration registration) {
        return new AppleOAuth2AuthorizationRequestResolver(
                new InMemoryClientRegistrationRepository(registration)
        );
    }

    private ClientRegistration clientRegistration(String registrationId, String authorizationUri) {
        return ClientRegistration.withRegistrationId(registrationId)
                .clientId(registrationId + "-client")
                .clientSecret("secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "email")
                .authorizationUri(authorizationUri)
                .tokenUri("https://example.com/token")
                .jwkSetUri("https://example.com/keys")
                .userNameAttributeName("sub")
                .clientName(registrationId)
                .build();
    }
}
