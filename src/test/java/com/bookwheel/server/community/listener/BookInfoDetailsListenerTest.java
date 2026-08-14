package com.bookwheel.server.community.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.dto.BookDetailResponse;
import com.bookwheel.server.community.entity.BookInfo;
import com.bookwheel.server.community.event.BookLikedEvent;
import com.bookwheel.server.community.repository.BookInfoRepository;
import com.bookwheel.server.community.service.BookDetailLookupService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookInfoDetailsListenerTest {

    private static final String ISBN = "9788954681179";
    private static final String TITLE = "Bright Night";
    private static final String AUTHOR = "Choi Eun-young";
    private static final String COVER_IMAGE = "https://image.aladin.co.kr/cover.jpg";

    @Mock private BookInfoRepository bookInfoRepository;
    @Mock private BookDetailLookupService bookDetailLookupService;

    @InjectMocks private BookInfoDetailsListener listener;

    @Test
    @DisplayName("Book like event stores looked-up book metadata")
    void onBookLiked_StoresBookDetails() {
        BookInfo bookInfo = BookInfo.builder().isbn(ISBN).build();
        given(bookInfoRepository.findByIsbn(ISBN)).willReturn(Optional.of(bookInfo));
        given(bookDetailLookupService.getBookDetailByIsbn(eq(ISBN), anyBoolean())).willReturn(sampleBookDetail());

        listener.onBookLiked(new BookLikedEvent(ISBN));

        assertThat(bookInfo.getTitle()).isEqualTo(TITLE);
        assertThat(bookInfo.getAuthor()).isEqualTo(AUTHOR);
        assertThat(bookInfo.getCoverImage()).isEqualTo(COVER_IMAGE);
    }

    @Test
    @DisplayName("Complete stored book metadata skips lookup")
    void onBookLiked_SkipsLookupWhenBookDetailsAlreadyStored() {
        BookInfo bookInfo = BookInfo.builder()
            .isbn(ISBN)
            .title(TITLE)
            .author(AUTHOR)
            .coverImage(COVER_IMAGE)
            .build();
        given(bookInfoRepository.findByIsbn(ISBN)).willReturn(Optional.of(bookInfo));

        listener.onBookLiked(new BookLikedEvent(ISBN));

        then(bookDetailLookupService).should(never()).getBookDetailByIsbn(any(), anyBoolean());
    }

    @Test
    @DisplayName("Stored cover alone is not complete book metadata")
    void onBookLiked_FillsMissingTitleAndAuthorWhenCoverAlreadyStored() {
        BookInfo bookInfo = BookInfo.builder()
            .isbn(ISBN)
            .coverImage(COVER_IMAGE)
            .build();
        given(bookInfoRepository.findByIsbn(ISBN)).willReturn(Optional.of(bookInfo));
        given(bookDetailLookupService.getBookDetailByIsbn(eq(ISBN), anyBoolean())).willReturn(sampleBookDetail());

        listener.onBookLiked(new BookLikedEvent(ISBN));

        assertThat(bookInfo.getTitle()).isEqualTo(TITLE);
        assertThat(bookInfo.getAuthor()).isEqualTo(AUTHOR);
        assertThat(bookInfo.getCoverImage()).isEqualTo(COVER_IMAGE);
    }

    @Test
    @DisplayName("Lookup failures do not escape the after-commit listener")
    void onBookLiked_SwallowsLookupFailure() {
        BookInfo bookInfo = BookInfo.builder().isbn(ISBN).build();
        given(bookInfoRepository.findByIsbn(ISBN)).willReturn(Optional.of(bookInfo));
        given(bookDetailLookupService.getBookDetailByIsbn(eq(ISBN), anyBoolean()))
            .willThrow(new BusinessException(ErrorCode.ALADIN_API_ERROR));

        assertThatCode(() -> listener.onBookLiked(new BookLikedEvent(ISBN))).doesNotThrowAnyException();
        assertThat(bookInfo.getCoverImage()).isNull();
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
