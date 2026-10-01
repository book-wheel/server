package com.bookwheel.server.common.oauth2.apple;

import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@RequiredArgsConstructor
public class AppleOAuth2TokenRequestParametersConverter implements
        Converter<OAuth2AuthorizationCodeGrantRequest, MultiValueMap<String, String>> {

    private final AppleClientSecretGenerator clientSecretGenerator;

    @Override
    public MultiValueMap<String, String> convert(OAuth2AuthorizationCodeGrantRequest grantRequest) {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        if (!"apple".equals(grantRequest.getClientRegistration().getRegistrationId())) {
            return parameters;
        }

        String clientSecret = clientSecretGenerator.generate(
                grantRequest.getClientRegistration().getClientId()
        );
        parameters.set(OAuth2ParameterNames.CLIENT_SECRET, clientSecret);
        return parameters;
    }
}
