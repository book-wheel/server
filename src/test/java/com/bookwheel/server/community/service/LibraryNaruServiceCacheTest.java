package com.bookwheel.server.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.bookwheel.server.community.dto.BookUsageAnalysisResponse;
import com.bookwheel.server.config.CacheConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 이용 분석 조회 캐시 동작 테스트.
 * Redis 없이 검증할 수 있도록 캐시 저장소만 인메모리로 바꿔서 @Cacheable 설정 자체를 확인한다.
 */
class LibraryNaruServiceCacheTest {

    private static final String API_URL = "https://data4library.kr/api/usageAnalysisList";
    private static final String ISBN = "9788954681179";

    private static final String SUCCESS_BODY = """
        {
          "response": {
            "book": {"loanCnt": 104490},
            "loanGrps": [{"loanGrp": {"age": "40대", "gender": "여성", "loanCnt": 384, "ranking": 144}}],
            "keywords": [{"keyword": {"word": "최은영", "weight": "7"}}]
          }
        }
        """;

    private static final String ERROR_BODY = """
        {"response": {"errCode": "authErr", "error": "인증정보가 일치하지 않습니다."}}
        """;

    @Configuration
    @EnableCaching
    static class CachingTestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CacheConfig.BOOK_USAGE_ANALYSIS);
        }
    }

    private ApplicationContextRunner contextRunner(RestClient restClient) {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(CachingTestConfig.class)
            .withBean(LibraryNaruService.class, () -> new LibraryNaruService(restClient, restClient))
            .withPropertyValues("naru.api.key=test-key", "naru.api.url=" + API_URL);
    }

    @Test
    @DisplayName("같은 ISBN을 다시 조회하면 외부 API를 호출하지 않고 캐시된 값을 반환한다.")
    void getUsageAnalysis_UsesCacheOnSecondCall() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        // 호출을 정확히 1회만 허용한다. 캐시가 동작하지 않으면 2회째에서 실패한다.
        server.expect(ExpectedCount.once(), requestTo(startsWith(API_URL)))
            .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        contextRunner(builder.build()).run(context -> {
            LibraryNaruService service = context.getBean(LibraryNaruService.class);

            BookUsageAnalysisResponse first = service.getUsageAnalysis(ISBN);
            BookUsageAnalysisResponse second = service.getUsageAnalysis(ISBN);

            assertThat(first).isNotNull();
            assertThat(second).isEqualTo(first);
            server.verify();
        });
    }

    @Test
    @DisplayName("ISBN이 다르면 각각 외부 API를 호출한다.")
    void getUsageAnalysis_CachesPerIsbn() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(ExpectedCount.times(2), requestTo(startsWith(API_URL)))
            .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        contextRunner(builder.build()).run(context -> {
            LibraryNaruService service = context.getBean(LibraryNaruService.class);

            service.getUsageAnalysis(ISBN);
            service.getUsageAnalysis("9791161571188");

            server.verify();
        });
    }

    @Test
    @DisplayName("조회에 실패해 null이 반환되면 캐시하지 않고 다음 요청에서 다시 호출한다.")
    void getUsageAnalysis_DoesNotCacheNull() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        // 실패 응답을 캐시해버리면 두 번째 호출이 나가지 않아 검증에 실패한다.
        server.expect(ExpectedCount.once(), requestTo(startsWith(API_URL))).andRespond(withServerError());
        server.expect(ExpectedCount.once(), requestTo(startsWith(API_URL)))
            .andRespond(withSuccess(ERROR_BODY, MediaType.APPLICATION_JSON));

        contextRunner(builder.build()).run(context -> {
            LibraryNaruService service = context.getBean(LibraryNaruService.class);

            assertThat(service.getUsageAnalysis(ISBN)).isNull();
            assertThat(service.getUsageAnalysis(ISBN)).isNull();

            server.verify();
        });
    }

    @Test
    @DisplayName("이용 분석 응답은 Redis 직렬화/역직렬화를 거쳐도 값이 보존된다.")
    void response_SurvivesRedisSerialization() {
        // 캐시에 저장할 때 실제로 쓰는 직렬화기로 왕복시켜 확인한다.
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();
        BookUsageAnalysisResponse original =
            new BookUsageAnalysisResponse(104490, "40대", List.of("역사", "소설", "광주민주화운동"));

        byte[] serialized = serializer.serialize(original);
        Object restored = serializer.deserialize(serialized);

        assertThat(restored).isInstanceOf(BookUsageAnalysisResponse.class);
        assertThat(restored).isEqualTo(original);
    }
}
