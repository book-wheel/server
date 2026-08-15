package com.bookwheel.server.community.service;


import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.dto.AladinBookSearchResponse;
import com.bookwheel.server.community.dto.BookDetailResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.UnknownContentTypeException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.beans.factory.annotation.Value;
import java.net.URI;


@Slf4j
@Service
public class AladinService {
    @Value("${aladin.api.key}")
    private String aladinApiKey;

    @Value("${aladin.api.url}")
    private String aladinApiUrl;

    private final RestClient restClient;

    public AladinService(@Qualifier("aladinRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public BookDetailResponse getBookDetailByIsbn(String isbn, boolean isInterested) {

        URI uri = UriComponentsBuilder.fromHttpUrl(aladinApiUrl)
            .queryParam("ttbkey", aladinApiKey)
            .queryParam("itemIdType", resolveItemIdType(isbn))
            .queryParam("ItemId", isbn)
            .queryParam("output", "js")
            .queryParam("Version", "20131101")
            .queryParam("Cover", "Big")
            .queryParam("OptResult", "itemPage,subInfo")
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
            logCallFailure(isbn, e);
            throw new BusinessException(ErrorCode.ALADIN_API_ERROR);
        }

        if (response == null || response.item() == null || response.item().isEmpty()) {
            throw new BusinessException(ErrorCode.BOOK_NOT_FOUND);
        }

        return BookDetailResponse.from(response.item().get(0), isInterested);
    }

    // 호출 실패는 원인이 달라도 모두 ALADIN_API_ERROR로 묶이므로, 원인만큼은 로그로 구분한다.
    // 요청 URI에는 ttbkey가 들어 있어 로그에 남기지 않는다.
    private void logCallFailure(String isbn, Exception e) {
        if (e instanceof ResourceAccessException) {
            log.error("알라딘 API 호출 실패(연결/응답 지연) - ISBN: {}, 원인: {}", isbn, e.getMessage());
            return;
        }
        if (e instanceof UnknownContentTypeException contentTypeException) {
            log.error(
                "알라딘 API 호출 실패(지원하지 않는 응답 형식) - ISBN: {}, Content-Type: {}",
                isbn,
                contentTypeException.getContentType()
            );
            return;
        }
        if (e instanceof RestClientResponseException responseException) {
            log.error(
                "알라딘 API 호출 실패(오류 응답) - ISBN: {}, status: {}",
                isbn,
                responseException.getStatusCode()
            );
            return;
        }
        log.error("알라딘 API 호출 실패(예상치 못한 오류) - ISBN: {}", isbn, e);
    }

    private String resolveItemIdType(String isbn) {
        return isbn != null && isbn.trim().length() == 10 ? "ISBN" : "ISBN13";
    }
}

