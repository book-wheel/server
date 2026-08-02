package com.bookwheel.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    @Primary
    public RestClient restClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);

        return builder
            .requestFactory(factory)
            .build();
    }

    // 도서관정보나루 이용 분석은 도서 상세 조회의 부가 정보이므로,
    // 외부 API 지연이 상세 조회 응답 시간에 그대로 더해지지 않도록 더 짧은 타임아웃을 사용한다.
    @Bean
    public RestClient naruRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);

        return builder
            .requestFactory(factory)
            .build();
    }
}
