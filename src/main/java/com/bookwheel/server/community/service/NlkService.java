package com.bookwheel.server.community.service;


import com.bookwheel.server.community.dto.NlkBookSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.beans.factory.annotation.Value;


import java.net.URI;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NlkService {
    @Value("${nlk.api.key}")
    private String nlkApiKey;

    @Value("${nlk.api.url}")
    private String nlkApiUrl;

    private final RestClient restClient = RestClient.builder()
        .messageConverters(converters -> {
            MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
            converter.setSupportedMediaTypes(List.of(
                MediaType.APPLICATION_JSON,
                MediaType.valueOf("text/json")
            ));
            converters.add(converter);
        })
        .build();

    //ISBN으로 국립중앙도서관 책 상세 정보 조회
    public NlkBookSearchResponse searchBookDetailByIsbn(String isbn) {
        log.info("국립중앙도서관 상세 조회 요청 - ISBN: {}", isbn);

        // 공식 문서 가이드에 맞춰 URL 및 파라미터 조립 [cite: 6, 7, 8]
        URI uri = UriComponentsBuilder.fromHttpUrl(nlkApiUrl)
            .queryParam("key", nlkApiKey)           // 발급받은 인증키
            .queryParam("apiType", "json")          // JSON 형태로 응답받기
            .queryParam("detailSearch", "true")     // 상세검색 모드 ON
            .queryParam("isbnOp", "isbn")           // 검색 조건: ISBN
            .queryParam("isbnCode", isbn)           // 실제 ISBN 값
            .build()
            .toUri();

        return restClient.get()
            .uri(uri)
            // 중앙도서관은 카카오처럼 헤더에 키를 넣지 않고 파라미터(?key=...)로 넘기기 때문에 header 세팅은 뺌.
            .retrieve()
            .body(NlkBookSearchResponse.class);
    }
}
