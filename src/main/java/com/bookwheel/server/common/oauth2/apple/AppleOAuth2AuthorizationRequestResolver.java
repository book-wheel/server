package com.bookwheel.server.common.oauth2.apple;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;

import java.util.LinkedHashMap;
import java.util.Map;

public class AppleOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private static final String AUTHORIZATION_REQUEST_BASE_URI = "/oauth2/authorization";

    private final OAuth2AuthorizationRequestResolver delegate;

    public AppleOAuth2AuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                AUTHORIZATION_REQUEST_BASE_URI
        );
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return customizeAppleRequest(delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return customizeAppleRequest(delegate.resolve(request, clientRegistrationId));
    }

    private OAuth2AuthorizationRequest customizeAppleRequest(OAuth2AuthorizationRequest authorizationRequest) {
        if (authorizationRequest == null || !isApple(authorizationRequest)) {
            return authorizationRequest;
        }

        Map<String, Object> additionalParameters = new LinkedHashMap<>(
                authorizationRequest.getAdditionalParameters()
        );
        // Apple requires form_post when user information scopes such as email are requested.
        additionalParameters.put("response_mode", "form_post");

        return OAuth2AuthorizationRequest.from(authorizationRequest)
                .additionalParameters(additionalParameters)
                .build();
    }

    private boolean isApple(OAuth2AuthorizationRequest authorizationRequest) {
        return "apple".equals(
                authorizationRequest.getAttribute(OAuth2ParameterNames.REGISTRATION_ID)
        );
    }
}
