package com.bookwheel.server.community.listener;

import com.bookwheel.server.community.dto.BookDetailResponse;
import com.bookwheel.server.community.entity.BookInfo;
import com.bookwheel.server.community.event.BookLikedEvent;
import com.bookwheel.server.community.repository.BookInfoRepository;
import com.bookwheel.server.community.service.AladinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 관심 도서 목록에서 외부 API 호출 없이 제목과 표지를 내려주기 위해, 찜 시점에 도서 정보를 저장해 둔다.
 *
 * 알라딘 호출은 느릴 수 있는 부가 작업이므로 찜 트랜잭션 안에서 하지 않는다.
 * 트랜잭션 안에 두면 외부 API 가 지연되는 동안 DB 커넥션이 함께 붙잡혀,
 * 처음 찜되는 ISBN 이 몰릴 때 커넥션 풀이 마르고 무관한 API 까지 영향을 받는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookInfoDetailsListener {

    private final BookInfoRepository bookInfoRepository;
    private final AladinService aladinService;

    /**
     * 커밋 이후에 실행되므로 찜 트랜잭션의 영속성 컨텍스트는 이미 닫혀 있다.
     * REQUIRES_NEW 로 새 트랜잭션을 열어야 변경 감지가 동작한다. (빠뜨리면 예외 없이 저장만 되지 않는다)
     *
     * 도서 정보 저장 실패가 이미 커밋된 찜을 되돌리면 안 되고, AFTER_COMMIT 콜백에서 던진 예외는
     * 커밋을 호출한 쪽까지 전파되므로 여기서 모두 삼킨다. 목록에서는 book 테이블 값으로 대체된다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBookLiked(BookLikedEvent event) {
        try {
            bookInfoRepository.findByIsbn(event.isbn())
                .filter(bookInfo -> !bookInfo.hasCoverImage())
                .ifPresent(this::applyBookDetails);
        } catch (Exception e) {
            log.warn("관심 도서 정보 저장 실패 - ISBN: {}, 원인: {}", event.isbn(), e.getMessage());
        }
    }

    private void applyBookDetails(BookInfo bookInfo) {
        BookDetailResponse bookDetail = aladinService.getBookDetailByIsbn(bookInfo.getIsbn(), true);
        bookInfo.applyBookDetailsIfAbsent(bookDetail.title(), bookDetail.author(), bookDetail.cover());
    }
}
