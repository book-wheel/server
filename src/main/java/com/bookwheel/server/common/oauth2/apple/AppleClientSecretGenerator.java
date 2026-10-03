package com.bookwheel.server.common.oauth2.apple;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.util.StringUtils;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

public class AppleClientSecretGenerator {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final Duration CLIENT_SECRET_TTL = Duration.ofMinutes(5);

    private final String teamId;
    private final String keyId;
    private final String privateKeyPem;
    private final Clock clock;

    private volatile PrivateKey privateKey;

    public AppleClientSecretGenerator(String teamId, String keyId, String privateKeyPem) {
        this(teamId, keyId, privateKeyPem, Clock.systemUTC());
    }

    AppleClientSecretGenerator(String teamId, String keyId, String privateKeyPem, Clock clock) {
        this.teamId = teamId;
        this.keyId = keyId;
        this.privateKeyPem = privateKeyPem;
        this.clock = clock;
    }

    public String generate(String clientId) {
        validateConfiguration(clientId);

        try {
            Instant issuedAt = clock.instant();
            return Jwts.builder()
                    .setHeaderParam("kid", keyId)
                    .setIssuer(teamId)
                    .setSubject(clientId)
                    .setAudience(APPLE_ISSUER)
                    .setIssuedAt(Date.from(issuedAt))
                    .setExpiration(Date.from(issuedAt.plus(CLIENT_SECRET_TTL)))
                    .signWith(getPrivateKey(), SignatureAlgorithm.ES256)
                    .compact();
        } catch (OAuth2AuthorizationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw appleConfigurationException("애플 client secret을 생성할 수 없습니다.", exception);
        }
    }

    private void validateConfiguration(String clientId) {
        if (!StringUtils.hasText(clientId)
                || !StringUtils.hasText(teamId)
                || !StringUtils.hasText(keyId)
                || !StringUtils.hasText(privateKeyPem)) {
            throw appleConfigurationException("애플 로그인 서버 설정이 완전하지 않습니다.", null);
        }
    }

    private PrivateKey getPrivateKey() throws Exception {
        PrivateKey resolved = privateKey;
        if (resolved != null) {
            return resolved;
        }

        synchronized (this) {
            if (privateKey == null) {
                privateKey = parsePrivateKey(privateKeyPem);
            }
            return privateKey;
        }
    }

    private PrivateKey parsePrivateKey(String pem) throws Exception {
        String normalized = pem.replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(normalized);
        PrivateKey parsed = KeyFactory.getInstance("EC")
                .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        if (!(parsed instanceof ECPrivateKey ecPrivateKey)
                || ecPrivateKey.getParams().getCurve().getField().getFieldSize() != 256) {
            throw new IllegalArgumentException("애플 private key는 P-256 EC 키여야 합니다.");
        }
        return parsed;
    }

    private OAuth2AuthorizationException appleConfigurationException(String description, Exception cause) {
        OAuth2Error error = new OAuth2Error("apple_client_secret_generation_failed", description, null);
        return cause == null
                ? new OAuth2AuthorizationException(error)
                : new OAuth2AuthorizationException(error, cause);
    }
}
