package com.bookwheel.server.common.oauth2.apple;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AppleOAuth2TokenRequestParametersConverterTest {

    @Test
    @DisplayName("Apple token 교환 요청의 정적 placeholder를 동적 client secret으로 덮어쓴다")
    void suppliesDynamicClientSecretForApple() {
        AppleClientSecretGenerator generator = mock(AppleClientSecretGenerator.class);
        OAuth2AuthorizationCodeGrantRequest grantRequest = mock(OAuth2AuthorizationCodeGrantRequest.class);
        given(grantRequest.getClientRegistration()).willReturn(registration("apple"));
        given(generator.generate("apple-client")).willReturn("signed-client-secret");
        AppleOAuth2TokenRequestParametersConverter converter =
                new AppleOAuth2TokenRequestParametersConverter(generator);

        MultiValueMap<String, String> parameters = converter.convert(grantRequest);

        assertThat(parameters.getFirst(OAuth2ParameterNames.CLIENT_SECRET))
                .isEqualTo("signed-client-secret");
        verify(generator).generate("apple-client");
    }

    @Test
    @DisplayName("Apple이 아닌 token 교환 요청은 client secret을 변경하지 않는다")
    void leavesOtherProvidersUntouched() {
        AppleClientSecretGenerator generator = mock(AppleClientSecretGenerator.class);
        OAuth2AuthorizationCodeGrantRequest grantRequest = mock(OAuth2AuthorizationCodeGrantRequest.class);
        given(grantRequest.getClientRegistration()).willReturn(registration("google"));
        AppleOAuth2TokenRequestParametersConverter converter =
                new AppleOAuth2TokenRequestParametersConverter(generator);

        MultiValueMap<String, String> parameters = converter.convert(grantRequest);

        assertThat(parameters).isEmpty();
        verifyNoInteractions(generator);
    }

    private ClientRegistration registration(String registrationId) {
        return ClientRegistration.withRegistrationId(registrationId)
                .clientId(registrationId + "-client")
                .clientSecret("static-placeholder")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "email")
                .authorizationUri("https://example.com/authorize")
                .tokenUri("https://example.com/token")
                .jwkSetUri("https://example.com/keys")
                .userNameAttributeName("sub")
                .clientName(registrationId)
                .build();
    }
}
