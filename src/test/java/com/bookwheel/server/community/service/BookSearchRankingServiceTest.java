package com.bookwheel.server.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.bookwheel.server.community.dto.BookSearchResponse;
import com.bookwheel.server.community.dto.BookSearchRankingResult;
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

        BookSearchRankingResult result = rankingService.rankByPopularity(books);

        assertThat(result.books()).extracting(BookSearchResponse::isbn)
            .containsExactly("9780000000003", "9780000000002", "9780000000001", "9780000000004");
        assertThat(result.ranking().source()).isEqualTo("DATA4LIBRARY");
        assertThat(result.ranking().sourceName()).isEqualTo("도서관 정보나루 인기대출도서");
        assertThat(result.ranking().startDate()).isEqualTo(START_DATE);
        assertThat(result.ranking().endDate()).isEqualTo(END_DATE);
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

        BookSearchRankingResult result = rankingService.rankByPopularity(books);

        assertThat(result.books()).extracting(BookSearchResponse::isbn)
            .containsExactly("9780000000002", "9780000000001", "9780000000003");
        assertThat(result.ranking().source()).isEqualTo("DATA4LIBRARY");
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

        BookSearchRankingResult result = rankingService.rankByPopularity(books);

        assertThat(result.books()).containsExactlyElementsOf(books);
        assertThat(result.ranking().source()).isEqualTo("KAKAO");
        assertThat(result.ranking().sourceName()).isEqualTo("카카오 도서 검색 API");
        assertThat(result.ranking().startDate()).isNull();
        assertThat(result.ranking().endDate()).isNull();
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

        BookSearchRankingResult result = rankingService.rankByPopularity(books);

        assertThat(result.books()).containsExactlyElementsOf(books);
        assertThat(result.ranking().source()).isEqualTo("KAKAO");
    }

    @Test
    @DisplayName("Popular title matches from Data4Library are merged into Kakao search results")
    void rankByPopularity_MergesData4LibraryTitleMatches() {
        List<BookSearchResponse> books = List.of(
            book("Vegetarian recipe", "9780000000001"),
            book("Vegetarian diet", "9780000000002")
        );
        PopularLoanBook snapshot = popularity("9780000000009", 1, 1000);
        PopularLoanBook vegetarian = popularity(
            "9788936433598",
            "채식주의자",
            "한강",
            "창비",
            "2007",
            "https://example.com/vegetarian.jpg",
            138,
            1396
        );
        given(popularLoanBookRepository.findFirstBySourceOrderByEndDateDescStartDateDescCollectedAtDesc(SOURCE))
            .willReturn(Optional.of(snapshot));
        given(popularLoanBookRepository.findBySourceAndStartDateAndEndDateAndIsbnIn(
            SOURCE,
            START_DATE,
            END_DATE,
            List.of("9780000000001", "9780000000002")
        )).willReturn(List.of());
        given(popularLoanBookRepository
            .findTop50BySourceAndStartDateAndEndDateAndTitleContainingIgnoreCaseOrderByRankAscLoanCountDesc(
                SOURCE,
                START_DATE,
                END_DATE,
                "채식"
            )).willReturn(List.of(vegetarian));

        BookSearchRankingResult result = rankingService.rankByPopularity(books, "채식");

        assertThat(result.books()).extracting(BookSearchResponse::isbn)
            .containsExactly("9788936433598", "9780000000001", "9780000000002");
        assertThat(result.books().get(0).title()).isEqualTo("채식주의자");
        assertThat(result.books().get(0).author()).isEqualTo("한강");
        assertThat(result.books().get(0).publisher()).isEqualTo("창비");
        assertThat(result.books().get(0).publishedDate()).isEqualTo("2007");
        assertThat(result.books().get(0).thumbnail()).isEqualTo("https://example.com/vegetarian.jpg");
        assertThat(result.ranking().source()).isEqualTo("DATA4LIBRARY");
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

    private PopularLoanBook popularity(
        String isbn,
        String title,
        String author,
        String publisher,
        String publishedDate,
        String thumbnail,
        int rank,
        int loanCount
    ) {
        return PopularLoanBook.builder()
            .isbn(isbn)
            .title(title)
            .author(author)
            .publisher(publisher)
            .publishedDate(publishedDate)
            .thumbnail(thumbnail)
            .rank(rank)
            .loanCount(loanCount)
            .collectedAt(COLLECTED_AT)
            .startDate(START_DATE)
            .endDate(END_DATE)
            .source(SOURCE)
            .build();
    }
}
