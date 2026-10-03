package com.bookwheel.server.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2ClientRegistrationConfigTest {

    @Test
    @DisplayName("Apple ClientRegistration에 issuer를 고정해 ID token의 iss claim을 검증한다")
    void configuresAppleIssuerWithoutDiscoveryRequest() {
        OAuth2ClientProperties properties = new OAuth2ClientProperties();
        properties.getRegistration().put("apple", appleRegistration());
        properties.getRegistration().put("google", googleRegistration());
        properties.getProvider().put("apple", appleProvider());

        ClientRegistrationRepository repository = new OAuth2ClientRegistrationConfig()
                .clientRegistrationRepository(properties);
        ClientRegistration apple = repository.findByRegistrationId("apple");

        assertThat(apple).isNotNull();
        assertThat(apple.getProviderDetails().getIssuerUri())
                .isEqualTo("https://appleid.apple.com");
        assertThat(apple.getProviderDetails().getJwkSetUri())
                .isEqualTo("https://appleid.apple.com/auth/keys");
        assertThat(apple.getScopes()).containsExactlyInAnyOrder("openid", "email");
        assertThat(repository.findByRegistrationId("google")).isNotNull();
    }

    private OAuth2ClientProperties.Registration appleRegistration() {
        OAuth2ClientProperties.Registration registration = new OAuth2ClientProperties.Registration();
        registration.setProvider("apple");
        registration.setClientId("kr.bookwheel.web");
        registration.setClientSecret("generated-dynamically");
        registration.setClientAuthenticationMethod("client_secret_post");
        registration.setAuthorizationGrantType("authorization_code");
        registration.setRedirectUri("{baseUrl}/login/oauth2/code/apple");
        registration.setScope(Set.of("openid", "email"));
        registration.setClientName("Apple");
        return registration;
    }

    private OAuth2ClientProperties.Provider appleProvider() {
        OAuth2ClientProperties.Provider provider = new OAuth2ClientProperties.Provider();
        provider.setAuthorizationUri("https://appleid.apple.com/auth/authorize");
        provider.setTokenUri("https://appleid.apple.com/auth/token");
        provider.setJwkSetUri("https://appleid.apple.com/auth/keys");
        provider.setUserNameAttribute("sub");
        return provider;
    }

    private OAuth2ClientProperties.Registration googleRegistration() {
        OAuth2ClientProperties.Registration registration = new OAuth2ClientProperties.Registration();
        registration.setClientId("google-client");
        registration.setClientSecret("google-secret");
        registration.setScope(Set.of("email"));
        return registration;
    }
}
