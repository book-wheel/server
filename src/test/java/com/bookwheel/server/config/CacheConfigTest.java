package com.bookwheel.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.data.redis.cache.RedisCacheManager;

class CacheConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class))
        .withUserConfiguration(CacheConfig.class);

    @Test
    @DisplayName("이용 분석 캐시가 Redis 기반으로 등록된다.")
    void cacheManager_IsConfigured() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(CacheManager.class);

            CacheManager cacheManager = context.getBean(CacheManager.class);
            assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
            assertThat(cacheManager.getCache(CacheConfig.BOOK_USAGE_ANALYSIS)).isNotNull();
        });
    }

    @Test
    @DisplayName("Redis 장애로 캐시 처리에 실패해도 예외를 전파하지 않는다.")
    void errorHandler_SwallowsCacheFailures() {
        // 캐시는 성능을 위한 수단이므로, Redis가 죽어도 도서 상세 조회가 실패하면 안 된다.
        var errorHandler = new CacheConfig().errorHandler();
        var cache = new ConcurrentMapCache(CacheConfig.BOOK_USAGE_ANALYSIS);
        var failure = new RuntimeException("Redis connection failure");

        assertThatCode(() -> {
            errorHandler.handleCacheGetError(failure, cache, "9788954681179");
            errorHandler.handleCachePutError(failure, cache, "9788954681179", null);
            errorHandler.handleCacheEvictError(failure, cache, "9788954681179");
            errorHandler.handleCacheClearError(failure, cache);
        }).doesNotThrowAnyException();
    }
}
