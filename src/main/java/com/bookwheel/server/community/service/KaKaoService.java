package com.bookwheel.server.community.service;

import com.bookwheel.server.community.dto.BookSearchRequest;
import com.bookwheel.server.community.dto.KakaoBookSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;


@Slf4j
@Component
@RequiredArgsConstructor
public class KaKaoService {
    @Value("${kakao.api.key}")
    private String kakaoApiKey;

    @Value("${kakao.api.url}")
    private String kakaoApiUrl;

    private final RestClient restClient = RestClient.create();

    public KakaoBookSearchResponse searchBooks(BookSearchRequest request) {
        log.info("카카오 도서 검색 요청 - query: {}, page: {}", request.query(), request.page());

        //한글 검색어 자동 인코딩
        URI uri = UriComponentsBuilder.fromHttpUrl(kakaoApiUrl)
            .queryParam("query", request.query())// 검색어
            .queryParam("sort", request.sort())// accuracy(정확도순)/latest(최신순)
            .queryParam("page", request.page())// 페이지 번호 (1~50)
            .queryParam("size", request.size())// 한 페이지 크기 (1~50)
            .build()
            .toUri();

        //조립된 URI로 카카오 공식 문서 기준에 맞춰 요청 보냄
        return restClient.get()
            .uri(uri)
            .header("Authorization", "KakaoAK " + kakaoApiKey)
            .retrieve()
            .body(KakaoBookSearchResponse.class);
    }
}
