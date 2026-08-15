package com.bookwheel.server.community.listener;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.event.BookLikedEvent;
import com.bookwheel.server.community.service.BookInfoDetailsUpdater;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class BookInfoDetailsListenerTest {

    private static final String ISBN = "9788954681179";

    @Mock private BookInfoDetailsUpdater bookInfoDetailsUpdater;

    @InjectMocks private BookInfoDetailsListener listener;

    @Test
    @DisplayName("Book like event triggers the book metadata update")
    void onBookLiked_DelegatesToUpdater() {
        listener.onBookLiked(new BookLikedEvent(ISBN));

        then(bookInfoDetailsUpdater).should().updateBookDetails(ISBN);
    }

    @Test
    @DisplayName("Lookup failures do not escape the after-commit listener")
    void onBookLiked_SwallowsLookupFailure() {
        willThrow(new BusinessException(ErrorCode.ALADIN_API_ERROR))
            .given(bookInfoDetailsUpdater).updateBookDetails(ISBN);

        assertThatCode(() -> listener.onBookLiked(new BookLikedEvent(ISBN))).doesNotThrowAnyException();
    }

    // 변경 감지 UPDATE 는 별도 트랜잭션의 커밋 시점에 실행된다.
    // 그 실패가 리스너 밖으로 나가면 이미 커밋된 찜이 남은 채로 API 만 실패한다.
    @Test
    @DisplayName("Commit-time save failures do not escape the after-commit listener")
    void onBookLiked_SwallowsSaveFailure() {
        willThrow(new DataIntegrityViolationException("Data too long for column 'author'"))
            .given(bookInfoDetailsUpdater).updateBookDetails(ISBN);

        assertThatCode(() -> listener.onBookLiked(new BookLikedEvent(ISBN))).doesNotThrowAnyException();
    }
}
