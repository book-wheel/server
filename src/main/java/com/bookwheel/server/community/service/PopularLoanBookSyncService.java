package com.bookwheel.server.community.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.dto.LibraryNaruPopularLoanResponse;
import com.bookwheel.server.community.dto.PopularLoanBookSyncResult;
import com.bookwheel.server.community.entity.PopularLoanBook;
import com.bookwheel.server.community.entity.PopularLoanBookSource;
import com.bookwheel.server.community.repository.PopularLoanBookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PopularLoanBookSyncService {

    private static final int FIRST_PAGE_NO = 1;
    private static final PopularLoanBookSource SOURCE = PopularLoanBookSource.DATA4LIBRARY;

    private final LibraryNaruService libraryNaruService;
    private final PopularLoanBookRepository popularLoanBookRepository;
    private final Clock clock;

    @Transactional
    public PopularLoanBookSyncResult syncPopularLoanBooks(LocalDate startDate, LocalDate endDate, int pageSize) {
        validateSyncRequest(startDate, endDate, pageSize);

        List<LibraryNaruPopularLoanResponse.Doc> docs = libraryNaruService.getPopularLoanBooks(
            startDate,
            endDate,
            FIRST_PAGE_NO,
            pageSize
        );
        List<PopularLoanBook> popularLoanBooks = toPopularLoanBooks(docs, startDate, endDate);

        // 조회 자체가 실패하면 LibraryNaruService가 예외를 던지므로, 여기 도달한 빈 결과는
        // 해당 기간에 집계된 데이터가 없는 정상 케이스다. 기존 스냅샷은 그대로 둔다.
        if (popularLoanBooks.isEmpty()) {
            log.warn(
                "Skip popular loan book sync because fetched data is empty - startDate: {}, endDate: {}",
                startDate,
                endDate
            );
            return PopularLoanBookSyncResult.skippedEmpty();
        }

        popularLoanBookRepository.deleteBySourceAndStartDateAndEndDate(SOURCE, startDate, endDate);
        popularLoanBookRepository.flush();
        popularLoanBookRepository.saveAll(popularLoanBooks);

        log.info(
            "Popular loan book sync completed - startDate: {}, endDate: {}, count: {}",
            startDate,
            endDate,
            popularLoanBooks.size()
        );
        return PopularLoanBookSyncResult.synced(popularLoanBooks.size());
    }

    private void validateSyncRequest(LocalDate startDate, LocalDate endDate, int pageSize) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate) || pageSize <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private List<PopularLoanBook> toPopularLoanBooks(
        List<LibraryNaruPopularLoanResponse.Doc> docs,
        LocalDate startDate,
        LocalDate endDate
    ) {
        if (docs == null || docs.isEmpty()) {
            return List.of();
        }

        LocalDateTime collectedAt = LocalDateTime.now(clock);
        Map<String, PopularLoanBook> booksByIsbn = new LinkedHashMap<>();

        for (LibraryNaruPopularLoanResponse.Doc doc : docs) {
            if (doc == null) {
                continue;
            }

            String isbn = normalizeIsbn(doc.isbn());
            if (isbn == null || doc.ranking() == null || doc.loanCount() == null) {
                continue;
            }

            booksByIsbn.putIfAbsent(
                isbn,
                PopularLoanBook.builder()
                    .isbn(isbn)
                    .title(normalizeText(doc.bookname()))
                    .author(normalizeText(doc.authors()))
                    .publisher(normalizeText(doc.publisher()))
                    .publishedDate(normalizeText(doc.publicationYear()))
                    .thumbnail(normalizeText(doc.bookImageUrl()))
                    .rank(doc.ranking())
                    .loanCount(doc.loanCount())
                    .collectedAt(collectedAt)
                    .startDate(startDate)
                    .endDate(endDate)
                    .source(SOURCE)
                    .build()
            );
        }

        return List.copyOf(booksByIsbn.values());
    }

    private String normalizeIsbn(String isbn) {
        if (isbn == null || isbn.isBlank()) {
            return null;
        }
        return isbn.trim();
    }

    private String normalizeText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }
}
