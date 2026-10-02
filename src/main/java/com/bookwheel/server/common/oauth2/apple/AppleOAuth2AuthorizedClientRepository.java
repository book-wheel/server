package com.bookwheel.server.common.oauth2.apple;

import com.bookwheel.server.common.oauth2.SocialLoginPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

@RequiredArgsConstructor
public class AppleOAuth2AuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

    private static final String APPLE_REGISTRATION_ID = "apple";

    private final AppleOAuthCredentialService credentialService;

    @Override
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
            String clientRegistrationId,
            Authentication principal,
            HttpServletRequest request
    ) {
        return null;
    }

    @Override
    public void saveAuthorizedClient(
            OAuth2AuthorizedClient authorizedClient,
            Authentication principal,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (!APPLE_REGISTRATION_ID.equals(authorizedClient.getClientRegistration().getRegistrationId())) {
            return;
        }
        if (!(principal.getPrincipal() instanceof SocialLoginPrincipal socialPrincipal)) {
            throw authenticationException("apple_principal_missing");
        }
        if (authorizedClient.getRefreshToken() == null) {
            throw authenticationException("apple_refresh_token_missing");
        }
        credentialService.store(
                socialPrincipal.getUserPK(),
                authorizedClient.getRefreshToken().getTokenValue()
        );
    }

    @Override
    public void removeAuthorizedClient(
            String clientRegistrationId,
            Authentication principal,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
    }

    private OAuth2AuthenticationException authenticationException(String code) {
        return new OAuth2AuthenticationException(new OAuth2Error(code), code);
    }
}
