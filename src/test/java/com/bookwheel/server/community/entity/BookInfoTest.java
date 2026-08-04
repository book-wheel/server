package com.bookwheel.server.community.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class BookInfoTest {

    private static final String ISBN = "9780132350884";

    private BookInfo bookInfo(String title) {
        return BookInfo.builder().isbn(ISBN).title(title).build();
    }

    @Test
    @DisplayName("제목이 비어 있으면 전달받은 제목으로 채운다.")
    void applyTitleIfAbsent_FillsWhenEmpty() {
        BookInfo bookInfo = bookInfo(null);

        bookInfo.applyTitleIfAbsent("Clean Code");

        assertThat(bookInfo.getTitle()).isEqualTo("Clean Code");
    }

    @Test
    @DisplayName("이미 저장된 제목이 있으면 덮어쓰지 않는다.")
    void applyTitleIfAbsent_KeepsExistingTitle() {
        BookInfo bookInfo = bookInfo("Clean Code");

        bookInfo.applyTitleIfAbsent("다른 제목");

        assertThat(bookInfo.getTitle()).isEqualTo("Clean Code");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("전달받은 제목이 null이거나 공백이면 저장하지 않는다.")
    void applyTitleIfAbsent_IgnoresBlankTitle(String title) {
        BookInfo bookInfo = bookInfo(null);

        bookInfo.applyTitleIfAbsent(title);

        assertThat(bookInfo.getTitle()).isNull();
    }
}
