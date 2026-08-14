package com.bookwheel.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

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
            .messageConverters(this::supportAladinJsonMediaTypes)
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

    // 알라딘 호출은 도서 상세 조회의 응답 시간에 그대로 더해지고, 관심 등록 후처리에서도 쓰인다.
    // 기본 클라이언트(5초/5초)는 연결과 응답이 각각 따로 소모되어 최악의 경우 10초를 대기하므로,
    // 알라딘 전용 클라이언트로 최악의 대기 시간을 6초로 제한한다.
    //
    // 실측값이 아니라 상한을 좁히기 위한 보수적인 값이다.
    // 타임아웃 발생률이 눈에 띄면 응답 시간을 측정해 정보나루처럼 근거를 남기고 조정한다.
    @Bean
    public RestClient aladinRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(4000);

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

    private void supportAladinJsonMediaTypes(List<HttpMessageConverter<?>> converters) {
        converters.stream()
            .filter(MappingJackson2HttpMessageConverter.class::isInstance)
            .map(MappingJackson2HttpMessageConverter.class::cast)
            .findFirst()
            .ifPresent(converter -> {
                List<MediaType> mediaTypes = new ArrayList<>(converter.getSupportedMediaTypes());
                addIfAbsent(mediaTypes, MediaType.valueOf("text/javascript"));
                addIfAbsent(mediaTypes, MediaType.valueOf("application/javascript"));
                converter.setSupportedMediaTypes(mediaTypes);
            });
    }

    private void addIfAbsent(List<MediaType> mediaTypes, MediaType mediaType) {
        if (!mediaTypes.contains(mediaType)) {
            mediaTypes.add(mediaType);
        }
    }
}
