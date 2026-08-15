package com.bookwheel.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.util.StringUtils;
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
    // 실측(50건) 기준 연결은 최대 0.10초, 서버 응답은 중앙값 0.09초, p95 0.18초, 최대 0.56초로
    // 정보나루보다 빠르고 편차도 작다. 현재 값이 정상 응답을 자르고 있지는 않아 그대로 둔다.
    //
    // 다만 2026-08-14 read timeout 장애처럼 스파이크는 발생하며, 이 경우 인기대출 메타데이터로
    // 대체된다(BookDetailLookupService). 실패 시 대체 경로가 있으므로 대기를 늘리기보다 현행 유지가 낫다.
    //
    // 위 실측은 배포 서버가 아닌 로컬에서 수행해 연결 구간은 참고값이다.
    // 타임아웃 발생률이 눈에 띄면 scripts/measure-aladin-latency.sh 를 배포 서버에서 실행해
    // 정보나루처럼 근거를 남기고 조정한다.
    @Bean
    public RestClient aladinRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(4000);

        return builder
            .requestFactory(factory)
            .messageConverters(this::supportAladinJsonMediaTypes)
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

    @Bean
    public RestClient expoPushRestClient(
            RestClient.Builder builder,
            @Value("${expo.push.url:https://exp.host/--/api/v2/push/send}") String pushUrl,
            @Value("${expo.push.access-token:}") String accessToken
    ) {
        return expoRestClient(builder, pushUrl, accessToken);
    }

    @Bean
    public RestClient expoPushReceiptRestClient(
            RestClient.Builder builder,
            @Value("${expo.push.receipts-url:https://exp.host/--/api/v2/push/getReceipts}") String receiptsUrl,
            @Value("${expo.push.access-token:}") String accessToken
    ) {
        return expoRestClient(builder, receiptsUrl, accessToken);
    }

    private RestClient expoRestClient(RestClient.Builder builder, String url, String accessToken) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);

        RestClient.Builder expoBuilder = builder
                .requestFactory(factory)
                .baseUrl(url)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (StringUtils.hasText(accessToken)) {
            expoBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken.trim());
        }
        return expoBuilder.build();
    }

    // 알라딘은 output=js 응답을 JSON이 아닌 미디어 타입으로 내려줄 수 있어, 역직렬화 실패를 막기 위한 방어 설정이다.
    // (2026-08-14 장애의 원인은 미디어 타입이 아니라 read timeout이었다. 이 설정은 그 장애의 대응책이 아니다.)
    //
    // Spring Boot는 모든 RestClient.Builder에 HttpMessageConverters 빈의 컨버터 "인스턴스"를 그대로 넣어준다.
    // 따라서 기존 컨버터의 지원 미디어 타입을 직접 바꾸면 다른 RestClient와 MVC 응답 컨버터까지 함께 바뀐다.
    // 알라딘 클라이언트에만 적용되도록 원본을 두고 사본으로 교체한다.
    private void supportAladinJsonMediaTypes(List<HttpMessageConverter<?>> converters) {
        converters.replaceAll(converter -> {
            if (converter instanceof MappingJackson2HttpMessageConverter jsonConverter) {
                return copyWithAladinJsonMediaTypes(jsonConverter);
            }
            return converter;
        });
    }

    private MappingJackson2HttpMessageConverter copyWithAladinJsonMediaTypes(
        MappingJackson2HttpMessageConverter converter
    ) {
        MappingJackson2HttpMessageConverter copy =
            new MappingJackson2HttpMessageConverter(converter.getObjectMapper());

        List<MediaType> mediaTypes = new ArrayList<>(converter.getSupportedMediaTypes());
        addIfAbsent(mediaTypes, MediaType.valueOf("text/javascript"));
        addIfAbsent(mediaTypes, MediaType.valueOf("application/javascript"));
        copy.setSupportedMediaTypes(mediaTypes);

        return copy;
    }

    private void addIfAbsent(List<MediaType> mediaTypes, MediaType mediaType) {
        if (!mediaTypes.contains(mediaType)) {
            mediaTypes.add(mediaType);
        }
    }
}
