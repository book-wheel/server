package com.bookwheel.server.common.oauth2.apple;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppleRefreshTokenCipherTest {

    @Test
    @DisplayName("Apple refresh token을 AES-GCM으로 암호화하고 복호화한다")
    void encryptsAndDecryptsRefreshToken() {
        AppleRefreshTokenCipher cipher = cipher("v1", Map.of("v1", key((byte) 1)));

        AppleRefreshTokenCipher.EncryptionResult result = cipher.encrypt("user-pk", "refresh-token");

        assertThat(result.encryptedToken()).doesNotContain("refresh-token");
        assertThat(result.keyVersion()).isEqualTo("v1");
        assertThat(cipher.decrypt("user-pk", result.keyVersion(), result.encryptedToken()))
                .isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("다른 userPK로는 refresh token을 복호화할 수 없다")
    void bindsCiphertextToUserPK() {
        AppleRefreshTokenCipher cipher = cipher("v1", Map.of("v1", key((byte) 2)));
        AppleRefreshTokenCipher.EncryptionResult result = cipher.encrypt("owner-pk", "refresh-token");

        assertThatThrownBy(() -> cipher.decrypt(
                "other-pk", result.keyVersion(), result.encryptedToken()
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("키 교체 후에도 구버전 키로 저장한 refresh token을 복호화한다")
    void decryptsTokenWithPreviousKeyAfterRotation() {
        AppleRefreshTokenCipher previousCipher = cipher("v1", Map.of("v1", key((byte) 3)));
        AppleRefreshTokenCipher.EncryptionResult encrypted =
                previousCipher.encrypt("user-pk", "refresh-token");
        Map<String, String> rotatedKeys = new LinkedHashMap<>();
        rotatedKeys.put("v1", key((byte) 3));
        rotatedKeys.put("v2", key((byte) 4));
        AppleRefreshTokenCipher rotatedCipher = cipher("v2", rotatedKeys);

        assertThat(rotatedCipher.decrypt(
                "user-pk", encrypted.keyVersion(), encrypted.encryptedToken()
        )).isEqualTo("refresh-token");
    }

    private AppleRefreshTokenCipher cipher(String activeVersion, Map<String, String> keys) {
        AppleTokenEncryptionProperties properties = new AppleTokenEncryptionProperties();
        properties.setActiveKeyVersion(activeVersion);
        properties.setKeys(new LinkedHashMap<>(keys));
        return new AppleRefreshTokenCipher(properties);
    }

    private String key(byte value) {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, value);
        return Base64.getEncoder().encodeToString(key);
    }
}
