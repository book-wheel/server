package com.bookwheel.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        // KST 기준 스케줄러와 서비스의 날짜 계산이 실행 환경의 기본 시간대에 따라 달라지지 않게 한다.
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
