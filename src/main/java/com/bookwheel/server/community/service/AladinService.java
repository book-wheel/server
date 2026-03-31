package com.bookwheel.server.community.service;


import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.dto.AladinBookSearchResponse;
import com.bookwheel.server.community.dto.BookDetailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.beans.factory.annotation.Value;
import java.net.URI;


@Slf4j
@Service
@RequiredArgsConstructor
public class AladinService {
    @Value("${aladin.api.key}")
    private String aladinApiKey;

    @Value("${aladin.api.url}")
    private String aladinApiUrl;

    private final RestClient restClient;


    public BookDetailResponse getBookDetailByIsbn(String isbn) {

        URI uri = UriComponentsBuilder.fromHttpUrl(aladinApiUrl)
            .queryParam("ttbkey", aladinApiKey)
            .queryParam("itemIdType", "ISBN13")
            .queryParam("ItemId", isbn)
            .queryParam("output", "js")
            .queryParam("Version", "20131101")
            .queryParam("OptResult", "toc,itemPage,subInfo")
            .build()
            .toUri();

        log.info("알라딘 도서 상세 조회 요청 - ISBN: {}", isbn);

        AladinBookSearchResponse response;
        try {
            response = restClient.get()
                .uri(uri)
                .retrieve()
                .body(AladinBookSearchResponse.class);
        } catch (Exception e) {
            log.error("알라딘 API 호출 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.ALADIN_API_ERROR);
        }

        if (response == null || response.item() == null || response.item().isEmpty()) {
            throw new BusinessException(ErrorCode.BOOK_NOT_FOUND);
        }

        return BookDetailResponse.from(response.item().get(0));
    }
}

