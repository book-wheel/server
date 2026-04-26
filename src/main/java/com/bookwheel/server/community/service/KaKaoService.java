package com.bookwheel.server.community.service;

import com.bookwheel.server.community.dto.BookSearchListResponse;
import com.bookwheel.server.community.dto.BookSearchRequest;
import com.bookwheel.server.community.dto.BookSearchResponse;
import com.bookwheel.server.community.dto.KakaoBookSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class KaKaoService {
    @Value("${kakao.api.key}")
    private String kakaoApiKey;

    @Value("${kakao.api.url}")
    private String kakaoApiUrl;

    private final RestClient restClient;

    public BookSearchListResponse searchBooks(BookSearchRequest request) {
        log.info("카카오 도서 검색 요청 - query: {}, page: {}", request.query(), request.page());

        //한글 검색어 자동 인코딩
        KakaoBookSearchResponse kakaoResponse = restClient.get()
            .uri(kakaoApiUrl, uriBuilder -> uriBuilder
                .queryParam("query", request.query())
                .queryParam("sort", request.sort())
                .queryParam("page", request.page())
                .queryParam("size", request.size())
                .build())
            .header("Authorization", "KakaoAK " + kakaoApiKey)
            .retrieve()
            .body(KakaoBookSearchResponse.class);

        if (kakaoResponse == null || kakaoResponse.documents() == null) {
            return new BookSearchListResponse(List.of(), 0, true);
        }

        List<BookSearchResponse> processedBooks = kakaoResponse.documents().stream()
            .map(doc -> BookSearchResponse.of(doc, extractIsbn13(doc.isbn())))
            .toList();

        return new BookSearchListResponse(
            processedBooks,
            kakaoResponse.meta().total_count(),
            kakaoResponse.meta().is_end()
        );
    }

    private String extractIsbn13(String isbn) {
        if (isbn == null || isbn.isBlank()) return "";
        String[] parts = isbn.split(" ");
        for (String part : parts) {
            if (part.length() == 13) return part;
        }
        return (parts.length > 0) ? parts[0] : "";
    }
}
