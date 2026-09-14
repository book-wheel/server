package com.bookwheel.server.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JpaAuditingConfigTest {

    @Test
    @DisplayName("Auditing 시각은 실행 환경의 기본 시간대가 아니라 주입된 Clock을 따른다")
    void kstDateTimeProvider_UsesInjectedClock() {
        Clock kstClock = Clock.fixed(Instant.parse("2026-09-03T03:00:00Z"), ZoneId.of("Asia/Seoul"));

        Optional<TemporalAccessor> now = new JpaAuditingConfig().kstDateTimeProvider(kstClock).getNow();

        assertThat(now).contains(LocalDateTime.of(2026, 9, 3, 12, 0, 0));
    }

    @Test
    @DisplayName("Auditing 설정은 KST DateTimeProvider를 참조한다")
    void enableJpaAuditing_ReferencesKstDateTimeProvider() {
        EnableJpaAuditing auditing = JpaAuditingConfig.class.getAnnotation(EnableJpaAuditing.class);

        assertThat(auditing.dateTimeProviderRef()).isEqualTo("kstDateTimeProvider");
    }
}
