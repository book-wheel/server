package com.bookwheel.server.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String BOOK_USAGE_ANALYSIS = "bookUsageAnalysis";

    // 대출 횟수는 규모를 보여주는 값이라 며칠 차이가 사용자에게 의미가 없고, 키워드는 서지정보 기반이라 거의 바뀌지 않는다.
    // 다만 연령대는 정보나루가 '최근 30일' 집계로 제공하므로, 집계 구간보다 캐시 기간이 길면
    // 만료 직전 사용자가 최대 두 달 전 정보를 보게 된다. 그래서 30일이 아닌 7일로 둔다.
    private static final Duration BOOK_USAGE_ANALYSIS_TTL = Duration.ofDays(7);

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration bookUsageAnalysisConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(BOOK_USAGE_ANALYSIS_TTL)
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(redisConnectionFactory)
            .withCacheConfiguration(BOOK_USAGE_ANALYSIS, bookUsageAnalysisConfig)
            .build();
    }

    /**
     * 캐시는 성능을 위한 수단이므로 Redis 장애가 기능 실패로 이어지면 안 된다.
     * 기본 동작은 예외를 그대로 전파하므로, 로그만 남기고 넘어가도록 재정의한다.
     * (캐시 조회에 실패하면 원래 메서드가 실행되어 외부 API를 직접 호출한다)
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {

            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("캐시 조회 실패 - cache: {}, key: {}, 원인: {}",
                    cache.getName(), key, exception.getClass().getSimpleName());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("캐시 저장 실패 - cache: {}, key: {}, 원인: {}",
                    cache.getName(), key, exception.getClass().getSimpleName());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("캐시 삭제 실패 - cache: {}, key: {}, 원인: {}",
                    cache.getName(), key, exception.getClass().getSimpleName());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("캐시 초기화 실패 - cache: {}, 원인: {}",
                    cache.getName(), exception.getClass().getSimpleName());
            }
        };
    }
}
