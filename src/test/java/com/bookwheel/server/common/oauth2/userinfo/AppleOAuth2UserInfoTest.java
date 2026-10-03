package com.bookwheel.server.common.oauth2.userinfo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AppleOAuth2UserInfoTest {

    @Test
    @DisplayName("Apple ID token claim에서 소셜 식별자와 Private Relay 이메일을 추출한다")
    void extractsAppleClaims() {
        AppleOAuth2UserInfo userInfo = new AppleOAuth2UserInfo(Map.of(
                "sub", "apple-subject",
                "email", "relay@privaterelay.appleid.com"
        ));

        assertThat(userInfo.getSocialId()).isEqualTo("apple-subject");
        assertThat(userInfo.getEmail()).isEqualTo("relay@privaterelay.appleid.com");
    }
}
