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

    /**
     * 해당 기간 스냅샷에 추천 순환에 필요한 후보가 모두 확보됐는지 확인한다.
     * 정보나루가 전월 집계를 며칠 늦게 공개하는 경우가 있어 스케줄러가 여러 날 재시도하는데,
     * 적재를 마친 날에는 외부 API를 다시 호출하지 않기 위해 사용한다.
     *
     * 존재 여부(exists)가 아니라 개수로 판단하는 이유는, 소량만 적재된 기간도 "적재됨"으로 보면
     * 이후 재시도가 모두 건너뛰어져 추천 후보가 한두 권으로 고정되기 때문이다.
     * 정보나루가 집계를 부분 공개했거나 수동 적재를 작은 pageSize 로 실행한 경우가 여기에 해당한다.
     *
     * 정보나루에 실제로 후보 수보다 적은 데이터밖에 없는 기간이면 남은 날에도 재시도하게 되지만,
     * 같은 데이터로 다시 적재할 뿐이고 더 공개되면 받아올 수 있으므로 이쪽이 의도에 맞다.
     */
    @Transactional(readOnly = true)
    public boolean hasEnoughCandidates(LocalDate startDate, LocalDate endDate) {
        long syncedCount = popularLoanBookRepository.countBySourceAndStartDateAndEndDate(
            SOURCE,
            startDate,
            endDate
        );
        return syncedCount >= PopularLoanBookRepository.RECOMMENDATION_CANDIDATE_COUNT;
    }

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
                "인기대출도서 조회 결과가 비어 적재를 건너뜁니다 - startDate: {}, endDate: {}",
                startDate,
                endDate
            );
            return PopularLoanBookSyncResult.skippedEmpty();
        }

        popularLoanBookRepository.deleteBySourceAndStartDateAndEndDate(SOURCE, startDate, endDate);
        popularLoanBookRepository.flush();
        popularLoanBookRepository.saveAll(popularLoanBooks);

        log.info(
            "인기대출도서 적재 완료 - startDate: {}, endDate: {}, count: {}",
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
