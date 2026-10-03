package com.bookwheel.server.common.oauth2.apple;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppleClientSecretGeneratorTest {

    private static final Instant NOW = Instant.parse("2026-09-30T00:00:00Z");

    @Test
    @DisplayName("Apple client secret을 ES256으로 서명해 생성한다")
    void generatesSignedClientSecret() throws Exception {
        KeyPair keyPair = createKeyPair();
        AppleClientSecretGenerator generator = new AppleClientSecretGenerator(
                "TEAM123456",
                "KEY1234567",
                escapedPem(keyPair),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        String clientSecret = generator.generate("kr.bookwheel.web");

        Jws<Claims> parsed = Jwts.parserBuilder()
                .setSigningKey(keyPair.getPublic())
                .setClock(() -> java.util.Date.from(NOW.plusSeconds(1)))
                .build()
                .parseClaimsJws(clientSecret);
        assertThat(parsed.getHeader().getAlgorithm()).isEqualTo("ES256");
        assertThat(parsed.getHeader().getKeyId()).isEqualTo("KEY1234567");
        assertThat(parsed.getBody().getIssuer()).isEqualTo("TEAM123456");
        assertThat(parsed.getBody().getSubject()).isEqualTo("kr.bookwheel.web");
        assertThat(parsed.getBody().getAudience()).isEqualTo("https://appleid.apple.com");
        assertThat(parsed.getBody().getIssuedAt().toInstant()).isEqualTo(NOW);
        assertThat(parsed.getBody().getExpiration().toInstant()).isEqualTo(NOW.plusSeconds(300));
    }

    @Test
    @DisplayName("Apple 서버 설정이 누락되면 client secret을 생성하지 않는다")
    void rejectsMissingConfiguration() {
        AppleClientSecretGenerator generator = new AppleClientSecretGenerator("", "", "");

        assertThatThrownBy(() -> generator.generate("kr.bookwheel.web"))
                .isInstanceOf(OAuth2AuthorizationException.class)
                .satisfies(exception -> assertThat(
                        ((OAuth2AuthorizationException) exception).getError().getErrorCode()
                ).isEqualTo("apple_client_secret_generation_failed"));
    }

    @Test
    @DisplayName("Apple private key가 P-256 EC 키가 아니면 client secret을 생성하지 않는다")
    void rejectsUnsupportedEcCurve() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(new ECGenParameterSpec("secp384r1"));
        AppleClientSecretGenerator generator = new AppleClientSecretGenerator(
                "TEAM123456",
                "KEY1234567",
                escapedPem(keyPairGenerator.generateKeyPair()),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> generator.generate("kr.bookwheel.web"))
                .isInstanceOf(OAuth2AuthorizationException.class)
                .satisfies(exception -> assertThat(
                        ((OAuth2AuthorizationException) exception).getError().getErrorCode()
                ).isEqualTo("apple_client_secret_generation_failed"));
    }

    private KeyPair createKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private String escapedPem(KeyPair keyPair) {
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(keyPair.getPrivate().getEncoded());
        return ("-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----")
                .replace("\n", "\\n");
    }
}
