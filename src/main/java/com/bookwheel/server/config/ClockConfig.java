package com.bookwheel.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        // 시간 의존 로직을 테스트에서 고정된 Clock으로 교체할 수 있게 한다.
        return Clock.systemDefaultZone();
    }
}
