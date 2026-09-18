package com.bookwheel.server.common.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AccessTokenRevocationService {

    private static final String KEY_PREFIX = "AUTH:REVOKED_USER:";
    // Access Token 30분에 서버 간 시각 오차를 고려한 5분을 더한다.
    private static final Duration REVOCATION_TTL = Duration.ofMinutes(35);

    private final StringRedisTemplate redisTemplate;

    public void revokeAllAccessTokens(String userPK) {
        redisTemplate.opsForValue().set(KEY_PREFIX + userPK, "true", REVOCATION_TTL);
    }

    public boolean isRevoked(String userPK) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + userPK));
    }
}
