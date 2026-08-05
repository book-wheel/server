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
    //
    // 실측(36건) 기준 연결은 최대 0.44초로 빠르지만 서버 응답은 중앙값 0.68초, p95 1.97초, 최대 4.17초로 편차가 크다.
    // 연결 대기는 줄이고 응답 대기에 여유를 주어, 최악의 대기 시간(4초)은 유지하면서 타임아웃 비율을 낮춘다.
    @Bean
    public RestClient naruRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(3000);

        return builder
            .requestFactory(factory)
            .build();
    }

    @Bean
    public RestClient naruPopularLoanRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);

        return builder
            .requestFactory(factory)
            .build();
    }
}
