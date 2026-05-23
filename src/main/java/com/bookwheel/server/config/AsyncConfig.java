package com.bookwheel.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 알림 등 도메인별 비동기 작업의 스레드 풀을 격리한다.
 * 기본 SimpleAsyncTaskExecutor 는 풀링이 없어 스케줄러로 다량 알림이 발행되면
 * 무제한 스레드 생성 위험이 있으므로 전용 ThreadPoolTaskExecutor 를 정의한다.
 */
@Configuration
public class AsyncConfig {

    @Bean("notificationTaskExecutor")
    public Executor notificationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("notif-");
        // 큐 포화 시 호출 스레드가 직접 처리 → 자연스러운 백프레셔
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
