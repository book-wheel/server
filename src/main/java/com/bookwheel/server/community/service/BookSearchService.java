package com.bookwheel.server.community.service;


import com.bookwheel.server.community.dto.AladinSearchResponseDto;
import com.bookwheel.server.community.dto.BookSearchResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookSearchService {

    private final RestTemplate restTemplate;

    @Value("${aladin.api.key}") // application.yml에 넣을 알라딘 키
    private String ttbKey;

    @Value("${aladin.api.search-url}") // https://www.aladin.co.kr/ttb/api/ItemSearch.aspx
    private String searchUrl;

    public List<BookSearchResponseDto> searchBooks(String keyword, String currentUserId) {
        // 1. 알라딘 API 요청 URL 만들기
        String requestUrl = String.format("%s?ttbkey=%s&Query=%s&QueryType=Keyword&MaxResults=24&start=1&SearchTarget=Book&output=js&Version=20131101",
            searchUrl, ttbKey, keyword);

        // 2. 알라딘 API 호출
        AladinSearchResponseDto aladinResponse = restTemplate.getForObject(requestUrl, AladinSearchResponseDto.class);

        // 결과가 없거나 에러가 났을 때 빈 리스트 반환
        if (aladinResponse == null || aladinResponse.item() == null) {
            return Collections.emptyList();
        }

        return aladinResponse.item().stream()
            .map(item -> new BookSearchResponseDto(
                item.isbn13(),
                item.title(),
                item.author(),
                item.publisher(),
                item.pubDate(),
                item.cover()
                // false (찜하기 필드 임시 제거)
            ))
            .toList();
    }

}
