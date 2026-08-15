package com.bookwheel.server.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.dto.BookDetailResponse;
import com.bookwheel.server.community.entity.PopularLoanBook;
import com.bookwheel.server.community.entity.PopularLoanBookSource;
import com.bookwheel.server.community.repository.PopularLoanBookRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookDetailLookupServiceTest {

    private static final String ISBN = "9788954681179";

    @Mock private AladinService aladinService;
    @Mock private PopularLoanBookRepository popularLoanBookRepository;

    @InjectMocks private BookDetailLookupService bookDetailLookupService;

    @Test
    @DisplayName("Aladin detail is used when lookup succeeds")
    void getBookDetailByIsbn_UsesAladinWhenAvailable() {
        BookDetailResponse aladinDetail = bookDetail("Aladin Title", "Aladin Author");
        given(aladinService.getBookDetailByIsbn(ISBN, true)).willReturn(aladinDetail);

        BookDetailResponse response = bookDetailLookupService.getBookDetailByIsbn(ISBN, true);

        assertThat(response).isSameAs(aladinDetail);
        then(popularLoanBookRepository).should(never())
            .findFirstByIsbnOrderByEndDateDescStartDateDescCollectedAtDesc(ISBN);
    }

    @Test
    @DisplayName("Popular loan metadata is used when Aladin lookup fails")
    void getBookDetailByIsbn_UsesPopularLoanMetadataFallbackWhenAladinFails() {
        given(aladinService.getBookDetailByIsbn(ISBN, true))
            .willThrow(new BusinessException(ErrorCode.ALADIN_API_ERROR));
        given(popularLoanBookRepository.findFirstByIsbnOrderByEndDateDescStartDateDescCollectedAtDesc(ISBN))
            .willReturn(Optional.of(popularLoanBook()));

        BookDetailResponse response = bookDetailLookupService.getBookDetailByIsbn(ISBN, true);

        assertThat(response.title()).isEqualTo("Recommended Title");
        assertThat(response.author()).isEqualTo("Recommended Author");
        assertThat(response.publisher()).isEqualTo("Recommended Publisher");
        assertThat(response.cover()).isEqualTo("https://example.com/recommended.jpg");
        assertThat(response.isbn()).isEqualTo(ISBN);
        assertThat(response.isInterested()).isTrue();
        assertThat(response.itemPage()).isNull();
        assertThat(response.usageAnalysis()).isNull();
    }

    @Test
    @DisplayName("Original lookup error is kept when no fallback metadata exists")
    void getBookDetailByIsbn_RethrowsOriginalExceptionWhenFallbackDoesNotExist() {
        given(aladinService.getBookDetailByIsbn(ISBN, false))
            .willThrow(new BusinessException(ErrorCode.BOOK_NOT_FOUND));
        given(popularLoanBookRepository.findFirstByIsbnOrderByEndDateDescStartDateDescCollectedAtDesc(ISBN))
            .willReturn(Optional.empty());

        assertThatThrownBy(() -> bookDetailLookupService.getBookDetailByIsbn(ISBN, false))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.BOOK_NOT_FOUND);
    }

    private BookDetailResponse bookDetail(String title, String author) {
        return new BookDetailResponse(
            title,
            author,
            "Publisher",
            "Description",
            "https://example.com/aladin.jpg",
            300,
            ISBN,
            true,
            null
        );
    }

    private PopularLoanBook popularLoanBook() {
        return PopularLoanBook.builder()
            .isbn(ISBN)
            .title("Recommended Title")
            .author("Recommended Author")
            .publisher("Recommended Publisher")
            .publishedDate("2026")
            .thumbnail("https://example.com/recommended.jpg")
            .rank(1)
            .loanCount(1000)
            .collectedAt(LocalDateTime.of(2026, 8, 1, 4, 0))
            .startDate(LocalDate.of(2026, 7, 1))
            .endDate(LocalDate.of(2026, 7, 31))
            .source(PopularLoanBookSource.DATA4LIBRARY)
            .build();
    }
}
