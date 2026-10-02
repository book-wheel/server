package com.bookwheel.server.common.oauth2.apple;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AppleRefreshTokenCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int AES_KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final String activeKeyVersion;
    private final Map<String, byte[]> keys;
    private final SecureRandom secureRandom;

    @Autowired
    public AppleRefreshTokenCipher(AppleTokenEncryptionProperties properties) {
        this(properties, new SecureRandom());
    }

    AppleRefreshTokenCipher(AppleTokenEncryptionProperties properties, SecureRandom secureRandom) {
        this.activeKeyVersion = requireKeyVersion(properties.getActiveKeyVersion());
        this.secureRandom = secureRandom;

        Map<String, byte[]> configuredKeys = new LinkedHashMap<>();
        properties.getKeys().forEach((version, encodedKey) -> {
            if (!StringUtils.hasText(encodedKey)) {
                return;
            }
            String validatedVersion = requireKeyVersion(version);
            byte[] key;
            try {
                key = Base64.getDecoder().decode(encodedKey.strip());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Apple refresh token 암호화 키가 Base64 형식이 아닙니다.", exception);
            }
            if (key.length != AES_KEY_BYTES) {
                throw new IllegalArgumentException("Apple refresh token 암호화 키는 32 byte여야 합니다.");
            }
            configuredKeys.put(validatedVersion, key);
        });
        if (!configuredKeys.containsKey(activeKeyVersion)) {
            throw new IllegalArgumentException("활성 Apple refresh token 암호화 키 버전이 설정되지 않았습니다.");
        }
        this.keys = Map.copyOf(configuredKeys);
    }

    public EncryptionResult encrypt(String userPK, String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new IllegalArgumentException("Apple refresh token은 필수입니다.");
        }
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        byte[] encrypted = crypt(
                Cipher.ENCRYPT_MODE,
                keys.get(activeKeyVersion),
                iv,
                associatedData(userPK, activeKeyVersion),
                refreshToken.getBytes(StandardCharsets.UTF_8)
        );
        byte[] payload = ByteBuffer.allocate(iv.length + encrypted.length)
                .put(iv)
                .put(encrypted)
                .array();
        return new EncryptionResult(activeKeyVersion, Base64.getEncoder().encodeToString(payload));
    }

    public String decrypt(String userPK, String keyVersion, String encryptedRefreshToken) {
        byte[] key = keys.get(keyVersion);
        if (key == null) {
            throw new IllegalStateException("Apple refresh token 복호화에 필요한 암호화 키가 없습니다.");
        }
        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(encryptedRefreshToken);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Apple refresh token 암호문이 손상되었습니다.", exception);
        }
        if (payload.length <= IV_BYTES) {
            throw new IllegalStateException("Apple refresh token 암호문이 손상되었습니다.");
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        byte[] iv = new byte[IV_BYTES];
        buffer.get(iv);
        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);
        byte[] decrypted = crypt(
                Cipher.DECRYPT_MODE,
                key,
                iv,
                associatedData(userPK, keyVersion),
                encrypted
        );
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private byte[] crypt(int mode, byte[] key, byte[] iv, byte[] associatedData, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(associatedData);
            return cipher.doFinal(input);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Apple refresh token 암호화 처리에 실패했습니다.", exception);
        }
    }

    private byte[] associatedData(String userPK, String keyVersion) {
        if (!StringUtils.hasText(userPK)) {
            throw new IllegalArgumentException("userPK는 필수입니다.");
        }
        return (userPK + "|APPLE|" + keyVersion).getBytes(StandardCharsets.UTF_8);
    }

    private String requireKeyVersion(String version) {
        if (!StringUtils.hasText(version) || !version.matches("^v[1-9][0-9]*$")) {
            throw new IllegalArgumentException("Apple refresh token 암호화 키 버전 형식이 아닙니다.");
        }
        return version;
    }

    public record EncryptionResult(String keyVersion, String encryptedToken) {
    }
}
