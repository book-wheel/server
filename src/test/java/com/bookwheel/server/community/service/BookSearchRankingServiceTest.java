package com.bookwheel.server.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bookwheel.server.community.dto.BookSearchResponse;
import com.bookwheel.server.community.entity.PopularLoanBook;
import com.bookwheel.server.community.entity.PopularLoanBookSource;
import com.bookwheel.server.community.repository.PopularLoanBookRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookSearchRankingServiceTest {

    private static final PopularLoanBookSource SOURCE = PopularLoanBookSource.DATA4LIBRARY;
    private static final LocalDate START_DATE = LocalDate.of(2026, 7, 1);
    private static final LocalDate END_DATE = LocalDate.of(2026, 7, 31);
    private static final LocalDateTime COLLECTED_AT = LocalDateTime.of(2026, 8, 1, 4, 0);

    @Mock
    private PopularLoanBookRepository popularLoanBookRepository;

    private BookSearchRankingService rankingService;

    @BeforeEach
    void setUp() {
        rankingService = new BookSearchRankingService(popularLoanBookRepository);
    }

    @Test
    @DisplayName("Books with Data4Library popularity are ranked before unmatched Kakao results")
    void rankByPopularity_RanksMatchedBooksBeforeUnmatchedBooks() {
        List<BookSearchResponse> books = List.of(
            book("Kakao first", "9780000000001"),
            book("Popular second", "9780000000002"),
            book("Popular first", "9780000000003"),
            book("Kakao last", "9780000000004")
        );
        PopularLoanBook snapshot = popularity("9780000000009", 1, 1000);
        given(popularLoanBookRepository.findFirstBySourceOrderByEndDateDescStartDateDescCollectedAtDesc(SOURCE))
            .willReturn(Optional.of(snapshot));
        given(popularLoanBookRepository.findBySourceAndStartDateAndEndDateAndIsbnIn(
            SOURCE,
            START_DATE,
            END_DATE,
            List.of("9780000000001", "9780000000002", "9780000000003", "9780000000004")
        )).willReturn(List.of(
            popularity("9780000000002", 3, 700),
            popularity("9780000000003", 1, 1000)
        ));

        List<BookSearchResponse> result = rankingService.rankByPopularity(books);

        assertThat(result).extracting(BookSearchResponse::isbn)
            .containsExactly("9780000000003", "9780000000002", "9780000000001", "9780000000004");
    }

    @Test
    @DisplayName("Loan count breaks ties when Data4Library ranks are equal")
    void rankByPopularity_UsesLoanCountWhenRankIsSame() {
        List<BookSearchResponse> books = List.of(
            book("Lower loan count", "9780000000001"),
            book("Higher loan count", "9780000000002"),
            book("Same popularity later", "9780000000003")
        );
        PopularLoanBook snapshot = popularity("9780000000009", 1, 1000);
        given(popularLoanBookRepository.findFirstBySourceOrderByEndDateDescStartDateDescCollectedAtDesc(SOURCE))
            .willReturn(Optional.of(snapshot));
        given(popularLoanBookRepository.findBySourceAndStartDateAndEndDateAndIsbnIn(
            SOURCE,
            START_DATE,
            END_DATE,
            List.of("9780000000001", "9780000000002", "9780000000003")
        )).willReturn(List.of(
            popularity("9780000000001", 1, 700),
            popularity("9780000000002", 1, 900),
            popularity("9780000000003", 1, 700)
        ));

        List<BookSearchResponse> result = rankingService.rankByPopularity(books);

        assertThat(result).extracting(BookSearchResponse::isbn)
            .containsExactly("9780000000002", "9780000000001", "9780000000003");
    }

    @Test
    @DisplayName("Original Kakao order is preserved when there is no popularity snapshot")
    void rankByPopularity_PreservesKakaoOrderWhenSnapshotDoesNotExist() {
        List<BookSearchResponse> books = List.of(
            book("Kakao first", "9780000000001"),
            book("Kakao second", "9780000000002")
        );
        given(popularLoanBookRepository.findFirstBySourceOrderByEndDateDescStartDateDescCollectedAtDesc(SOURCE))
            .willReturn(Optional.empty());

        List<BookSearchResponse> result = rankingService.rankByPopularity(books);

        assertThat(result).containsExactlyElementsOf(books);
        then(popularLoanBookRepository).should()
            .findFirstBySourceOrderByEndDateDescStartDateDescCollectedAtDesc(SOURCE);
        then(popularLoanBookRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("Original Kakao order is preserved when no searched ISBN has popularity data")
    void rankByPopularity_PreservesKakaoOrderWhenNoIsbnMatches() {
        List<BookSearchResponse> books = List.of(
            book("Kakao first", "9780000000001"),
            book("Kakao second", "9780000000002")
        );
        PopularLoanBook snapshot = popularity("9780000000009", 1, 1000);
        given(popularLoanBookRepository.findFirstBySourceOrderByEndDateDescStartDateDescCollectedAtDesc(SOURCE))
            .willReturn(Optional.of(snapshot));
        given(popularLoanBookRepository.findBySourceAndStartDateAndEndDateAndIsbnIn(
            SOURCE,
            START_DATE,
            END_DATE,
            List.of("9780000000001", "9780000000002")
        )).willReturn(List.of());

        List<BookSearchResponse> result = rankingService.rankByPopularity(books);

        assertThat(result).containsExactlyElementsOf(books);
    }

    private BookSearchResponse book(String title, String isbn) {
        return new BookSearchResponse(
            title,
            "author",
            "publisher",
            "2026-01-01",
            "https://example.com/thumbnail.jpg",
            isbn,
            false
        );
    }

    private PopularLoanBook popularity(String isbn, int rank, int loanCount) {
        return PopularLoanBook.builder()
            .isbn(isbn)
            .rank(rank)
            .loanCount(loanCount)
            .collectedAt(COLLECTED_AT)
            .startDate(START_DATE)
            .endDate(END_DATE)
            .source(SOURCE)
            .build();
    }
}
