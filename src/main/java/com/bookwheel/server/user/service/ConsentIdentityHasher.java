package com.bookwheel.server.user.service;

import com.bookwheel.server.user.config.ConsentIdentityHmacProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ConsentIdentityHasher {

    public static final String HASH_ALGORITHM = "HMAC_SHA256";
    private static final String ALGORITHM = "HmacSHA256";
    private static final int MINIMUM_SECRET_LENGTH = 32;

    private final String activeKeyVersion;
    private final Map<String, byte[]> keys;

    public ConsentIdentityHasher(
            ConsentIdentityHmacProperties properties
    ) {
        this.activeKeyVersion = requireKeyVersion(properties.getActiveKeyVersion());
        Map<String, byte[]> configuredKeys = new LinkedHashMap<>();
        properties.getKeys().forEach((version, secret) ->
                addKey(configuredKeys, requireKeyVersion(version), secret));
        if (!configuredKeys.containsKey(this.activeKeyVersion)) {
            throw new IllegalArgumentException("활성 동의 증빙 HMAC 키 버전이 설정되지 않았습니다.");
        }
        this.keys = Map.copyOf(configuredKeys);
    }

    public HashResult hashEmail(String email) {
        return new HashResult(activeKeyVersion, hash(normalizeEmail(email), keys.get(activeKeyVersion)));
    }

    public List<HashResult> hashEmailWithAllKeys(String email) {
        String normalizedEmail = normalizeEmail(email);
        return keys.entrySet().stream()
                .map(entry -> new HashResult(entry.getKey(), hash(normalizedEmail, entry.getValue())))
                .toList();
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("동의 당사자 이메일은 필수입니다.");
        }
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private String hash(String normalizedEmail, byte[] secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(normalizedEmail.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("동의 당사자 식별 해시를 생성할 수 없습니다.", exception);
        }
    }

    private void addKey(Map<String, byte[]> configuredKeys, String version, String secret) {
        if (!StringUtils.hasText(secret)) {
            return;
        }
        if (secret.length() < MINIMUM_SECRET_LENGTH) {
            throw new IllegalArgumentException("동의 증빙 HMAC 비밀키는 32자 이상이어야 합니다.");
        }
        configuredKeys.put(version, secret.getBytes(StandardCharsets.UTF_8));
    }

    private String requireKeyVersion(String version) {
        if (!StringUtils.hasText(version) || !version.matches("^v[1-9][0-9]*$")) {
            throw new IllegalArgumentException("동의 증빙 HMAC 키 버전 형식이 올바르지 않습니다.");
        }
        return version;
    }

    public record HashResult(String keyVersion, String hash) {
    }
}
