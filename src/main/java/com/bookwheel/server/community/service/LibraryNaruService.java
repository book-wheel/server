package com.bookwheel.server.community.service;


import com.bookwheel.server.community.dto.BookUsageAnalysisResponse;
import com.bookwheel.server.community.dto.LibraryNaruUsageAnalysisResponse;
import com.bookwheel.server.config.CacheConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;


@Slf4j
@Service
public class LibraryNaruService {

    @Value("${naru.api.key}")
    private String naruApiKey;

    @Value("${naru.api.url}")
    private String naruApiUrl;

    private final RestClient restClient;

    public LibraryNaruService(@Qualifier("naruRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * ISBN으로 도서 이용 분석 정보를 조회한다.
     * 도서 상세 조회의 부가 정보이므로 호출 실패, 타임아웃, 파싱 오류 등 어떤 문제도 밖으로 전파하지 않고 null을 반환한다.
     *
     * 정보나루는 인증키별 호출 한도가 있고 대출 통계는 일 단위로만 갱신되므로 ISBN 단위로 캐시한다.
     * 조회에 실패했거나 데이터가 없으면(null) 캐시하지 않는다. 일시적인 장애를 오래 붙잡아두지 않기 위해서다.
     */
    @Cacheable(cacheNames = CacheConfig.BOOK_USAGE_ANALYSIS, key = "#isbn", unless = "#result == null")
    public BookUsageAnalysisResponse getUsageAnalysis(String isbn) {

        log.info("도서관정보나루 이용 분석 조회 요청 - ISBN: {}", isbn);

        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(naruApiUrl)
                .queryParam("authKey", naruApiKey)
                .queryParam("isbn13", isbn)
                .queryParam("format", "json")
                .build()
                .toUri();

            LibraryNaruUsageAnalysisResponse response = restClient.get()
                .uri(uri)
                .retrieve()
                .body(LibraryNaruUsageAnalysisResponse.class);

            if (response == null || response.response() == null) {
                log.warn("도서관정보나루 응답이 비어 있습니다 - ISBN: {}", isbn);
                return null;
            }

            // 인증 오류나 조회 실패도 HTTP 200으로 내려오므로 errCode로 판별한다.
            if (response.response().errCode() != null) {
                log.warn("도서관정보나루 조회 실패 - ISBN: {}, errCode: {}", isbn, response.response().errCode());
                return null;
            }

            // 캐시에는 가공이 끝난 작은 응답만 저장한다. (키워드 50건 등 원본 전체를 담지 않기 위해)
            return BookUsageAnalysisResponse.from(response.response());
        } catch (Exception e) {
            // 인증키가 노출되지 않도록 요청 URI나 예외 메시지 대신 예외 타입만 남긴다.
            log.warn("도서관정보나루 호출 실패 - ISBN: {}, 원인: {}", isbn, e.getClass().getSimpleName());
            return null;
        }
    }
}
