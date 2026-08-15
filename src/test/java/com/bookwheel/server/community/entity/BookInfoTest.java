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

    // 알라딘 저자 필드는 역할이 나열돼 실측 최대 504자까지 나왔다. 컬럼을 넘기면 저장이 실패하므로 경계에서 자른다.
    @Test
    @DisplayName("Overlong title and author are truncated to the column length")
    void applyBookDetailsIfAbsent_TruncatesOverlongText() {
        BookInfo bookInfo = BookInfo.builder().isbn(ISBN).build();
        String title = "가".repeat(BookInfo.TITLE_MAX_LENGTH + 50);
        String author = "나".repeat(BookInfo.AUTHOR_MAX_LENGTH + 50);

        bookInfo.applyBookDetailsIfAbsent(title, author, "https://example.com/cover.jpg");

        assertThat(bookInfo.getTitle()).hasSize(BookInfo.TITLE_MAX_LENGTH);
        assertThat(bookInfo.getAuthor()).hasSize(BookInfo.AUTHOR_MAX_LENGTH);
        assertThat(title).startsWith(bookInfo.getTitle());
        assertThat(author).startsWith(bookInfo.getAuthor());
    }

    @Test
    @DisplayName("Values within the column length are stored as they are")
    void applyBookDetailsIfAbsent_KeepsValuesWithinLength() {
        BookInfo bookInfo = BookInfo.builder().isbn(ISBN).build();
        String author = "다".repeat(BookInfo.AUTHOR_MAX_LENGTH);

        bookInfo.applyBookDetailsIfAbsent("Clean Code", author, "https://example.com/cover.jpg");

        assertThat(bookInfo.getAuthor()).isEqualTo(author);
    }

    // 잘린 URL 은 깨진 이미지가 되므로 저장하지 않는다. 목록에서는 book 테이블 값으로 대체된다.
    @Test
    @DisplayName("Overlong cover URL is skipped instead of truncated")
    void applyBookDetailsIfAbsent_SkipsOverlongCoverUrl() {
        BookInfo bookInfo = BookInfo.builder().isbn(ISBN).build();
        String coverImage = "https://example.com/" + "a".repeat(BookInfo.COVER_IMAGE_MAX_LENGTH);

        bookInfo.applyBookDetailsIfAbsent("Clean Code", "Robert C. Martin", coverImage);

        assertThat(bookInfo.getCoverImage()).isNull();
        assertThat(bookInfo.getTitle()).isEqualTo("Clean Code");
    }

    // 서로게이트 쌍 중간에서 자르면 깨진 문자가 남아 저장 시 인코딩 오류가 날 수 있다.
    @Test
    @DisplayName("Truncation does not split a surrogate pair")
    void applyTitleIfAbsent_DoesNotSplitSurrogatePair() {
        BookInfo bookInfo = BookInfo.builder().isbn(ISBN).build();
        String title = "가".repeat(BookInfo.TITLE_MAX_LENGTH - 1) + "😀" + "나";

        bookInfo.applyTitleIfAbsent(title);

        assertThat(bookInfo.getTitle()).hasSize(BookInfo.TITLE_MAX_LENGTH - 1);
        assertThat(bookInfo.getTitle()).doesNotContain("\uD83D");
    }
}
