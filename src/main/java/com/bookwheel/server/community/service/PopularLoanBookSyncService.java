package com.bookwheel.server.community.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.dto.LibraryNaruPopularLoanResponse;
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
    public int syncPopularLoanBooks(LocalDate startDate, LocalDate endDate, int pageSize) {
        validateSyncRequest(startDate, endDate, pageSize);

        List<LibraryNaruPopularLoanResponse.Doc> docs = libraryNaruService.getPopularLoanBooks(
            startDate,
            endDate,
            FIRST_PAGE_NO,
            pageSize
        );
        List<PopularLoanBook> popularLoanBooks = toPopularLoanBooks(docs, startDate, endDate);

        if (popularLoanBooks.isEmpty()) {
            log.warn(
                "Skip popular loan book sync because fetched data is empty - startDate: {}, endDate: {}",
                startDate,
                endDate
            );
            return 0;
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
        return popularLoanBooks.size();
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
}
