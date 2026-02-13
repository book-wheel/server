package com.bookwheel.server.common.jwt;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

@AllArgsConstructor
@Getter
@RedisHash(value = "refreshToken", timeToLive = 1209600) // 2주
public class RefreshToken {

    @Id
    private String userId;

    private String refreshToken;
}