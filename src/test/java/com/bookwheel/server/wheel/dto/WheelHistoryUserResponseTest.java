package com.bookwheel.server.wheel.dto;

import com.bookwheel.server.book.entity.Book;
import com.bookwheel.server.book.entity.OwnBook;
import com.bookwheel.server.wheel.entity.WheelState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WheelHistoryUserResponseTest {

    @Test
    @DisplayName("멤버 독서 내역은 책별 상세 조회에 필요한 OwnBook 식별자와 표지를 포함한다")
    void of_IncludesOwnBookIdAndCoverImageUrl() {
        Book book = Book.builder()
                .bookId("book-1")
                .title("테스트 도서")
                .coverImage("https://example.com/cover.jpg")
                .build();
        OwnBook ownBook = OwnBook.builder()
                .ownBookId("own-book-1")
                .book(book)
                .build();
        WheelState wheelState = WheelState.builder()
                .wheelStateId("wheel-state-1")
                .ownBook(ownBook)
                .build();

        WheelHistoryUserResponse response = WheelHistoryUserResponse.of(
                wheelState,
                2,
                List.of("https://example.com/review.jpg")
        );

        assertThat(response.ownBookId()).isEqualTo("own-book-1");
        assertThat(response.coverImageUrl()).isEqualTo("https://example.com/cover.jpg");
    }
}
