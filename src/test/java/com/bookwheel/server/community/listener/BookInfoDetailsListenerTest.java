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
import com.bookwheel.server.community.service.AladinService;
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

    @Mock private BookInfoRepository bookInfoRepository;
    @Mock private AladinService aladinService;

    @InjectMocks private BookInfoDetailsListener listener;

    @Test
    @DisplayName("관심 등록 이벤트를 받으면 알라딘에서 조회한 도서 정보를 저장한다.")
    void onBookLiked_StoresBookDetails() {
        BookInfo bookInfo = BookInfo.builder().isbn(ISBN).build();
        given(bookInfoRepository.findByIsbn(ISBN)).willReturn(Optional.of(bookInfo));
        given(aladinService.getBookDetailByIsbn(eq(ISBN), anyBoolean())).willReturn(sampleBookDetail());

        listener.onBookLiked(new BookLikedEvent(ISBN));

        assertThat(bookInfo.getTitle()).isEqualTo("밝은 밤");
        assertThat(bookInfo.getAuthor()).isEqualTo("최은영");
        assertThat(bookInfo.getCoverImage()).isEqualTo("https://image.aladin.co.kr/cover.jpg");
    }

    @Test
    @DisplayName("이벤트 처리 시점에 이미 표지가 채워져 있으면 외부 API를 호출하지 않는다.")
    void onBookLiked_SkipsLookupWhenCoverAlreadyStored() {
        BookInfo bookInfo = BookInfo.builder()
            .isbn(ISBN)
            .title("밝은 밤")
            .author("최은영")
            .coverImage("https://image.aladin.co.kr/cover.jpg")
            .build();
        given(bookInfoRepository.findByIsbn(ISBN)).willReturn(Optional.of(bookInfo));

        listener.onBookLiked(new BookLikedEvent(ISBN));

        then(aladinService).should(never()).getBookDetailByIsbn(any(), anyBoolean());
    }

    @Test
    @DisplayName("도서 정보 조회에 실패해도 예외를 밖으로 전파하지 않는다.")
    void onBookLiked_SwallowsLookupFailure() {
        BookInfo bookInfo = BookInfo.builder().isbn(ISBN).build();
        given(bookInfoRepository.findByIsbn(ISBN)).willReturn(Optional.of(bookInfo));
        given(aladinService.getBookDetailByIsbn(eq(ISBN), anyBoolean()))
            .willThrow(new BusinessException(ErrorCode.ALADIN_API_ERROR));

        assertThatCode(() -> listener.onBookLiked(new BookLikedEvent(ISBN))).doesNotThrowAnyException();
        assertThat(bookInfo.getCoverImage()).isNull();
    }

    private BookDetailResponse sampleBookDetail() {
        return new BookDetailResponse(
            "밝은 밤",
            "최은영",
            "문학동네",
            "소개",
            "https://image.aladin.co.kr/cover.jpg",
            340,
            ISBN,
            true,
            null
        );
    }
}
