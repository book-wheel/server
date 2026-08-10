package com.bookwheel.server.common.oauth2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2PkceTest {

    @Test
    @DisplayName("RFC 7636 S256 방식으로 code challenge를 생성한다")
    void createsS256CodeChallenge() {
        String codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

        String codeChallenge = OAuth2Pkce.createS256CodeChallenge(codeVerifier);

        assertThat(codeChallenge).isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }
}
