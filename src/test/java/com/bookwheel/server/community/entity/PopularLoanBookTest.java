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
            .rank(1)
            .loanCount(104490)
            .collectedAt(collectedAt)
            .startDate(startDate)
            .endDate(endDate)
            .source(PopularLoanBookSource.DATA4LIBRARY)
            .build();

        assertThat(popularLoanBook.getIsbn()).isEqualTo("9788954681179");
        assertThat(popularLoanBook.getRank()).isEqualTo(1);
        assertThat(popularLoanBook.getLoanCount()).isEqualTo(104490);
        assertThat(popularLoanBook.getCollectedAt()).isEqualTo(collectedAt);
        assertThat(popularLoanBook.getStartDate()).isEqualTo(startDate);
        assertThat(popularLoanBook.getEndDate()).isEqualTo(endDate);
        assertThat(popularLoanBook.getSource()).isEqualTo(PopularLoanBookSource.DATA4LIBRARY);
    }

    @Test
    @DisplayName("동일 ISBN 스냅샷의 인기 순위와 대출횟수를 갱신한다")
    void updatePopularity_UpdatesRankLoanCountAndCollectedAt() {
        PopularLoanBook popularLoanBook = PopularLoanBook.builder()
            .isbn("9788954681179")
            .rank(10)
            .loanCount(100)
            .collectedAt(LocalDateTime.of(2026, 8, 1, 4, 0))
            .startDate(LocalDate.of(2026, 7, 1))
            .endDate(LocalDate.of(2026, 7, 31))
            .source(PopularLoanBookSource.DATA4LIBRARY)
            .build();
        LocalDateTime newCollectedAt = LocalDateTime.of(2026, 8, 2, 4, 0);

        popularLoanBook.updatePopularity(1, 200, newCollectedAt);

        assertThat(popularLoanBook.getRank()).isEqualTo(1);
        assertThat(popularLoanBook.getLoanCount()).isEqualTo(200);
        assertThat(popularLoanBook.getCollectedAt()).isEqualTo(newCollectedAt);
    }
}
