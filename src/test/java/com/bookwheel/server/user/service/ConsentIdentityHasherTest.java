package com.bookwheel.server.user.service;

import com.bookwheel.server.user.config.ConsentIdentityHmacProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsentIdentityHasherTest {

    private final ConsentIdentityHasher hasher = createHasher();

    @Test
    @DisplayName("이메일 대소문자와 양끝 공백을 정규화해 같은 당사자로 식별한다")
    void hashEmailNormalizesIdentity() {
        assertThat(hasher.hashEmail("User@Example.com").hash())
                .isEqualTo(hasher.hashEmail("  user@example.COM  ").hash())
                .hasSize(64);
        assertThat(hasher.hashEmail("user@example.com").keyVersion()).isEqualTo("v1");
        assertThat(hasher.hashEmailWithAllKeys("user@example.com")).hasSize(2);
    }

    @Test
    @DisplayName("동의 증빙 비밀키는 32자 이상이어야 한다")
    void constructorRejectsWeakSecret() {
        ConsentIdentityHmacProperties properties = new ConsentIdentityHmacProperties();
        properties.setActiveKeyVersion("v1");
        properties.setKeys(java.util.Map.of("v1", "too-short"));
        assertThatThrownBy(() -> new ConsentIdentityHasher(properties))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ConsentIdentityHasher createHasher() {
        ConsentIdentityHmacProperties properties = new ConsentIdentityHmacProperties();
        properties.setActiveKeyVersion("v1");
        properties.setKeys(java.util.Map.of(
                "v1", "test-consent-identity-secret-at-least-32-chars",
                "v2", "test-consent-identity-v2-secret-at-least-32-chars"
        ));
        return new ConsentIdentityHasher(properties);
    }
}
