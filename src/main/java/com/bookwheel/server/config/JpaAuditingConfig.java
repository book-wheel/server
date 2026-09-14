package com.bookwheel.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "kstDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public DateTimeProvider kstDateTimeProvider(Clock clock) {
        // 기본 DateTimeProvider는 JVM 기본 시간대를 사용해 컨테이너(UTC)에서 KST보다 9시간 이른 값이 저장된다.
        return () -> Optional.of(LocalDateTime.now(clock));
    }
}
