package com.bookwheel.server.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.dto.LibraryNaruPopularLoanResponse;
import com.bookwheel.server.community.dto.PopularLoanBookSyncResult;
import com.bookwheel.server.community.dto.PopularLoanBookSyncStatus;
import com.bookwheel.server.community.entity.PopularLoanBook;
import com.bookwheel.server.community.entity.PopularLoanBookSource;
import com.bookwheel.server.community.repository.PopularLoanBookRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PopularLoanBookSyncServiceTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 7, 1);
    private static final LocalDate END_DATE = LocalDate.of(2026, 7, 31);
    private static final LocalDateTime COLLECTED_AT = LocalDateTime.of(2026, 8, 1, 4, 0);
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final int PAGE_SIZE = 1000;

    @Mock
    private LibraryNaruService libraryNaruService;

    @Mock
    private PopularLoanBookRepository popularLoanBookRepository;

    private PopularLoanBookSyncService syncService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(COLLECTED_AT.atZone(SEOUL_ZONE).toInstant(), SEOUL_ZONE);
        syncService = new PopularLoanBookSyncService(libraryNaruService, popularLoanBookRepository, clock);
    }

    @Test
    @DisplayName("Fetched popular loan books are stored as a Data4Library snapshot")
    void syncPopularLoanBooks_ReplacesSnapshotWithFetchedBooks() {
        given(libraryNaruService.getPopularLoanBooks(START_DATE, END_DATE, 1, PAGE_SIZE)).willReturn(List.of(
            doc(1, "9788954681179", 104490),
            doc(2, " 9780132350884 ", 90000)
        ));

        PopularLoanBookSyncResult result = syncService.syncPopularLoanBooks(START_DATE, END_DATE, PAGE_SIZE);

        assertThat(result.status()).isEqualTo(PopularLoanBookSyncStatus.SYNCED);
        assertThat(result.syncedCount()).isEqualTo(2);
        then(popularLoanBookRepository).should()
            .deleteBySourceAndStartDateAndEndDate(PopularLoanBookSource.DATA4LIBRARY, START_DATE, END_DATE);
        then(popularLoanBookRepository).should().flush();

        List<PopularLoanBook> savedBooks = capturedSavedBooks();
        assertThat(savedBooks).extracting(PopularLoanBook::getIsbn)
            .containsExactly("9788954681179", "9780132350884");
        assertThat(savedBooks).extracting(PopularLoanBook::getTitle)
            .containsExactly("book", "book");
        assertThat(savedBooks).extracting(PopularLoanBook::getAuthor)
            .containsExactly("author", "author");
        assertThat(savedBooks).extracting(PopularLoanBook::getPublisher)
            .containsExactly("publisher", "publisher");
        assertThat(savedBooks).extracting(PopularLoanBook::getPublishedDate)
            .containsExactly("2026", "2026");
        assertThat(savedBooks).extracting(PopularLoanBook::getThumbnail)
            .containsExactly("https://example.com/book.jpg", "https://example.com/book.jpg");
        assertThat(savedBooks).extracting(PopularLoanBook::getRank)
            .containsExactly(1, 2);
        assertThat(savedBooks).extracting(PopularLoanBook::getLoanCount)
            .containsExactly(104490, 90000);
        assertThat(savedBooks).extracting(PopularLoanBook::getCollectedAt)
            .containsOnly(COLLECTED_AT);
        assertThat(savedBooks).extracting(PopularLoanBook::getStartDate)
            .containsOnly(START_DATE);
        assertThat(savedBooks).extracting(PopularLoanBook::getEndDate)
            .containsOnly(END_DATE);
        assertThat(savedBooks).extracting(PopularLoanBook::getSource)
            .containsOnly(PopularLoanBookSource.DATA4LIBRARY);
    }

    @Test
    @DisplayName("Duplicate ISBNs keep the first Data4Library ranking")
    void syncPopularLoanBooks_DeduplicatesByIsbnKeepingFirst() {
        given(libraryNaruService.getPopularLoanBooks(START_DATE, END_DATE, 1, PAGE_SIZE)).willReturn(List.of(
            doc(2, "9788954681179", 90000),
            doc(1, "9788954681179", 104490)
        ));

        PopularLoanBookSyncResult result = syncService.syncPopularLoanBooks(START_DATE, END_DATE, PAGE_SIZE);

        assertThat(result.syncedCount()).isEqualTo(1);
        List<PopularLoanBook> savedBooks = capturedSavedBooks();
        assertThat(savedBooks).hasSize(1);
        assertThat(savedBooks.get(0).getIsbn()).isEqualTo("9788954681179");
        assertThat(savedBooks.get(0).getRank()).isEqualTo(2);
        assertThat(savedBooks.get(0).getLoanCount()).isEqualTo(90000);
    }

    @Test
    @DisplayName("Invalid fetched docs are skipped before storing the snapshot")
    void syncPopularLoanBooks_SkipsInvalidFetchedDocs() {
        given(libraryNaruService.getPopularLoanBooks(START_DATE, END_DATE, 1, PAGE_SIZE)).willReturn(Arrays.asList(
            null,
            doc(null, "9788954681179", 104490),
            doc(2, " ", 90000),
            doc(3, "9780132350884", null),
            doc(4, "9788966262281", 80000)
        ));

        PopularLoanBookSyncResult result = syncService.syncPopularLoanBooks(START_DATE, END_DATE, PAGE_SIZE);

        assertThat(result.syncedCount()).isEqualTo(1);
        List<PopularLoanBook> savedBooks = capturedSavedBooks();
        assertThat(savedBooks).hasSize(1);
        assertThat(savedBooks.get(0).getIsbn()).isEqualTo("9788966262281");
        assertThat(savedBooks.get(0).getRank()).isEqualTo(4);
        assertThat(savedBooks.get(0).getLoanCount()).isEqualTo(80000);
    }

    @Test
    @DisplayName("Empty fetched data is reported as SKIPPED_EMPTY and keeps the previous snapshot")
    void syncPopularLoanBooks_SkipsReplacementWhenFetchedDataIsEmpty() {
        given(libraryNaruService.getPopularLoanBooks(START_DATE, END_DATE, 1, PAGE_SIZE)).willReturn(List.of());

        PopularLoanBookSyncResult result = syncService.syncPopularLoanBooks(START_DATE, END_DATE, PAGE_SIZE);

        // 조회 실패(예외)와 구분되도록 상태로 표시하고, 적재 건수 0을 성공처럼 남기지 않는다.
        assertThat(result.status()).isEqualTo(PopularLoanBookSyncStatus.SKIPPED_EMPTY);
        assertThat(result.isSkippedEmpty()).isTrue();
        assertThat(result.syncedCount()).isZero();
        then(popularLoanBookRepository).should(never())
            .deleteBySourceAndStartDateAndEndDate(PopularLoanBookSource.DATA4LIBRARY, START_DATE, END_DATE);
        then(popularLoanBookRepository).should(never()).flush();
        then(popularLoanBookRepository).should(never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Invalid sync period throws INVALID_INPUT_VALUE")
    void syncPopularLoanBooks_ThrowsWhenPeriodIsInvalid() {
        assertThatThrownBy(() -> syncService.syncPopularLoanBooks(END_DATE, START_DATE, PAGE_SIZE))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(libraryNaruService, popularLoanBookRepository);
    }

    @Test
    @DisplayName("Invalid page size throws INVALID_INPUT_VALUE")
    void syncPopularLoanBooks_ThrowsWhenPageSizeIsInvalid() {
        assertThatThrownBy(() -> syncService.syncPopularLoanBooks(START_DATE, END_DATE, 0))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(libraryNaruService, popularLoanBookRepository);
    }

    @Test
    @DisplayName("A period holding all recommendation candidates is treated as synced")
    void hasEnoughCandidates_ReturnsTrueWhenCandidateCountIsSecured() {
        given(popularLoanBookRepository.countBySourceAndStartDateAndEndDate(
            PopularLoanBookSource.DATA4LIBRARY, START_DATE, END_DATE
        )).willReturn((long) PopularLoanBookRepository.RECOMMENDATION_CANDIDATE_COUNT);

        assertThat(syncService.hasEnoughCandidates(START_DATE, END_DATE)).isTrue();
    }

    @Test
    @DisplayName("A period holding only a few books is not treated as synced")
    void hasEnoughCandidates_ReturnsFalseWhenOnlyAFewBooksAreStored() {
        // 수동 적재를 작은 pageSize 로 실행했거나 정보나루가 집계를 부분 공개한 경우,
        // 행이 있다는 이유로 건너뛰면 추천 후보가 소수로 고정된다.
        given(popularLoanBookRepository.countBySourceAndStartDateAndEndDate(
            PopularLoanBookSource.DATA4LIBRARY, START_DATE, END_DATE
        )).willReturn(1L);

        assertThat(syncService.hasEnoughCandidates(START_DATE, END_DATE)).isFalse();
    }

    private LibraryNaruPopularLoanResponse.Doc doc(Integer ranking, String isbn, Integer loanCount) {
        return new LibraryNaruPopularLoanResponse.Doc(
            ranking,
            isbn,
            loanCount,
            "book",
            "author",
            "publisher",
            "2026",
            "https://example.com/book.jpg"
        );
    }

    @SuppressWarnings("unchecked")
    private List<PopularLoanBook> capturedSavedBooks() {
        ArgumentCaptor<List<PopularLoanBook>> captor = ArgumentCaptor.forClass(List.class);
        then(popularLoanBookRepository).should().saveAll(captor.capture());
        return captor.getValue();
    }
}
