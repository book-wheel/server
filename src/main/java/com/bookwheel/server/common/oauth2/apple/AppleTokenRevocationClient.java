package com.bookwheel.server.common.oauth2.apple;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class AppleTokenRevocationClient {

    private static final String REVOCATION_URI = "https://appleid.apple.com/auth/revoke";

    private final RestClient restClient;
    private final AppleClientSecretGenerator clientSecretGenerator;
    private final String clientId;

    public AppleTokenRevocationClient(
            RestClient.Builder restClientBuilder,
            AppleClientSecretGenerator clientSecretGenerator,
            @Value("${spring.security.oauth2.client.registration.apple.client-id:}") String clientId
    ) {
        this(restClientBuilder.clone().build(), clientSecretGenerator, clientId);
    }

    AppleTokenRevocationClient(
            RestClient restClient,
            AppleClientSecretGenerator clientSecretGenerator,
            String clientId
    ) {
        this.restClient = restClient;
        this.clientSecretGenerator = clientSecretGenerator;
        this.clientId = clientId;
    }

    public void revokeRefreshToken(String refreshToken) {
        if (!StringUtils.hasText(clientId)) {
            throw new IllegalStateException("Apple client-id가 설정되지 않았습니다.");
        }
        if (!StringUtils.hasText(refreshToken)) {
            throw new IllegalArgumentException("Apple refresh token은 필수입니다.");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecretGenerator.generate(clientId));
        form.add("token", refreshToken);
        form.add("token_type_hint", "refresh_token");

        restClient.post()
                .uri(REVOCATION_URI)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }
}
