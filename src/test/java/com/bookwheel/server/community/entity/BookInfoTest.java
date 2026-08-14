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
    @DisplayName("Missing title is filled")
    void applyTitleIfAbsent_FillsWhenEmpty() {
        BookInfo bookInfo = bookInfo(null);

        bookInfo.applyTitleIfAbsent("Clean Code");

        assertThat(bookInfo.getTitle()).isEqualTo("Clean Code");
    }

    @Test
    @DisplayName("Existing title is kept")
    void applyTitleIfAbsent_KeepsExistingTitle() {
        BookInfo bookInfo = bookInfo("Clean Code");

        bookInfo.applyTitleIfAbsent("Other Title");

        assertThat(bookInfo.getTitle()).isEqualTo("Clean Code");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("Blank stored title is filled")
    void applyTitleIfAbsent_FillsWhenStoredTitleIsBlank(String storedTitle) {
        BookInfo bookInfo = bookInfo(storedTitle);

        bookInfo.applyTitleIfAbsent("Clean Code");

        assertThat(bookInfo.getTitle()).isEqualTo("Clean Code");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("Blank incoming title is ignored")
    void applyTitleIfAbsent_IgnoresBlankTitle(String title) {
        BookInfo bookInfo = bookInfo(null);

        bookInfo.applyTitleIfAbsent(title);

        assertThat(bookInfo.getTitle()).isNull();
    }

    @Test
    @DisplayName("Book metadata is complete only when title, author, and cover are all present")
    void hasBookDetails_ReturnsTrueWhenAllMetadataExists() {
        BookInfo bookInfo = BookInfo.builder()
            .isbn(ISBN)
            .title("Clean Code")
            .author("Robert C. Martin")
            .coverImage("https://example.com/cover.jpg")
            .build();

        assertThat(bookInfo.hasBookDetails()).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("Blank title keeps book metadata incomplete")
    void hasBookDetails_ReturnsFalseWhenRequiredMetadataIsBlank(String title) {
        BookInfo bookInfo = BookInfo.builder()
            .isbn(ISBN)
            .title(title)
            .author("Robert C. Martin")
            .coverImage("https://example.com/cover.jpg")
            .build();

        assertThat(bookInfo.hasBookDetails()).isFalse();
    }
}
