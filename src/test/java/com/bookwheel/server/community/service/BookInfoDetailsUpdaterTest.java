package com.bookwheel.server.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bookwheel.server.community.dto.BookDetailResponse;
import com.bookwheel.server.community.entity.BookInfo;
import com.bookwheel.server.community.repository.BookInfoRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookInfoDetailsUpdaterTest {

    private static final String ISBN = "9788954681179";
    private static final String TITLE = "Bright Night";
    private static final String AUTHOR = "Choi Eun-young";
    private static final String COVER_IMAGE = "https://image.aladin.co.kr/cover.jpg";

    @Mock private BookInfoRepository bookInfoRepository;
    @Mock private BookDetailLookupService bookDetailLookupService;

    @InjectMocks private BookInfoDetailsUpdater updater;

    @Test
    @DisplayName("Looked-up book metadata is stored")
    void updateBookDetails_StoresBookDetails() {
        BookInfo bookInfo = BookInfo.builder().isbn(ISBN).build();
        given(bookInfoRepository.findByIsbn(ISBN)).willReturn(Optional.of(bookInfo));
        given(bookDetailLookupService.getBookDetailByIsbn(eq(ISBN), anyBoolean())).willReturn(sampleBookDetail());

        updater.updateBookDetails(ISBN);

        assertThat(bookInfo.getTitle()).isEqualTo(TITLE);
        assertThat(bookInfo.getAuthor()).isEqualTo(AUTHOR);
        assertThat(bookInfo.getCoverImage()).isEqualTo(COVER_IMAGE);
    }

    @Test
    @DisplayName("Complete stored book metadata skips lookup")
    void updateBookDetails_SkipsLookupWhenBookDetailsAlreadyStored() {
        BookInfo bookInfo = BookInfo.builder()
            .isbn(ISBN)
            .title(TITLE)
            .author(AUTHOR)
            .coverImage(COVER_IMAGE)
            .build();
        given(bookInfoRepository.findByIsbn(ISBN)).willReturn(Optional.of(bookInfo));

        updater.updateBookDetails(ISBN);

        then(bookDetailLookupService).should(never()).getBookDetailByIsbn(any(), anyBoolean());
    }

    @Test
    @DisplayName("Stored cover alone is not complete book metadata")
    void updateBookDetails_FillsMissingTitleAndAuthorWhenCoverAlreadyStored() {
        BookInfo bookInfo = BookInfo.builder()
            .isbn(ISBN)
            .coverImage(COVER_IMAGE)
            .build();
        given(bookInfoRepository.findByIsbn(ISBN)).willReturn(Optional.of(bookInfo));
        given(bookDetailLookupService.getBookDetailByIsbn(eq(ISBN), anyBoolean())).willReturn(sampleBookDetail());

        updater.updateBookDetails(ISBN);

        assertThat(bookInfo.getTitle()).isEqualTo(TITLE);
        assertThat(bookInfo.getAuthor()).isEqualTo(AUTHOR);
        assertThat(bookInfo.getCoverImage()).isEqualTo(COVER_IMAGE);
    }

    private BookDetailResponse sampleBookDetail() {
        return new BookDetailResponse(
            TITLE,
            AUTHOR,
            "Munhakdongne",
            "Book description",
            COVER_IMAGE,
            340,
            ISBN,
            true,
            null
        );
    }
}
