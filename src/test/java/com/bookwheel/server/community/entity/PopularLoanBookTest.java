package com.bookwheel.server.community.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PopularLoanBookTest {

    @Test
    @DisplayName("정보나루 인기대출도서 적재 필드를 저장한다")
    void popularLoanBook_StoresData4LibrarySnapshotFields() {
        LocalDate startDate = LocalDate.of(2026, 7, 1);
        LocalDate endDate = LocalDate.of(2026, 7, 31);
        LocalDateTime collectedAt = LocalDateTime.of(2026, 8, 1, 4, 0);

        PopularLoanBook popularLoanBook = PopularLoanBook.builder()
            .isbn("9788954681179")
            .title("Bright Night")
            .author("Author")
            .publisher("Publisher")
            .publishedDate("2021")
            .thumbnail("https://example.com/book.jpg")
            .rank(1)
            .loanCount(104490)
            .collectedAt(collectedAt)
            .startDate(startDate)
            .endDate(endDate)
            .source(PopularLoanBookSource.DATA4LIBRARY)
            .build();

        assertThat(popularLoanBook.getIsbn()).isEqualTo("9788954681179");
        assertThat(popularLoanBook.getTitle()).isEqualTo("Bright Night");
        assertThat(popularLoanBook.getAuthor()).isEqualTo("Author");
        assertThat(popularLoanBook.getPublisher()).isEqualTo("Publisher");
        assertThat(popularLoanBook.getPublishedDate()).isEqualTo("2021");
        assertThat(popularLoanBook.getThumbnail()).isEqualTo("https://example.com/book.jpg");
        assertThat(popularLoanBook.getRank()).isEqualTo(1);
        assertThat(popularLoanBook.getLoanCount()).isEqualTo(104490);
        assertThat(popularLoanBook.getCollectedAt()).isEqualTo(collectedAt);
        assertThat(popularLoanBook.getStartDate()).isEqualTo(startDate);
        assertThat(popularLoanBook.getEndDate()).isEqualTo(endDate);
        assertThat(popularLoanBook.getSource()).isEqualTo(PopularLoanBookSource.DATA4LIBRARY);
    }
}
