package com.bookwheel.server.common.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AccessTokenRevocationServiceTest {

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("탈퇴 회원의 모든 기존 Access Token을 35분간 거부하도록 표시한다")
    void revokesUserAccessTokens() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        AccessTokenRevocationService service = new AccessTokenRevocationService(redisTemplate);

        service.revokeAllAccessTokens("user-pk");

        verify(valueOperations).set("AUTH:REVOKED_USER:user-pk", "true", Duration.ofMinutes(35));
        given(redisTemplate.hasKey("AUTH:REVOKED_USER:user-pk")).willReturn(true);
        assertThat(service.isRevoked("user-pk")).isTrue();
    }
}
