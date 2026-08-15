package com.bookwheel.server.community.service;

import com.bookwheel.server.community.dto.BookDetailResponse;
import com.bookwheel.server.community.entity.BookInfo;
import com.bookwheel.server.community.repository.BookInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 찜 이후 도서 정보를 저장하는 별도 트랜잭션.
 *
 * 리스너와 빈을 나눈 이유는 트랜잭션 경계 때문이다.
 * 변경 감지로 만들어지는 UPDATE 는 메서드가 반환된 뒤 커밋 시점에 실행되므로,
 * @Transactional 이 리스너 메서드에 붙어 있으면 그 실패가 리스너의 try/catch 밖에서 발생한다.
 * AFTER_COMMIT 콜백에서 빠져나간 예외는 찜 커밋을 호출한 쪽까지 전파되어,
 * 찜은 저장됐는데 API 는 실패하는 상태가 된다.
 *
 * 경계를 이 빈으로 옮기면 커밋이 updateBookDetails 안에서 끝나므로 리스너가 실패를 삼킬 수 있다.
 * 같은 클래스에서 메서드만 나누면 프록시를 타지 않아 효과가 없다.
 */
@Service
@RequiredArgsConstructor
public class BookInfoDetailsUpdater {

    private final BookInfoRepository bookInfoRepository;
    private final BookDetailLookupService bookDetailLookupService;

    /**
     * 커밋 이후에 호출되므로 찜 트랜잭션의 영속성 컨텍스트는 이미 닫혀 있다.
     * REQUIRES_NEW 로 새 트랜잭션을 열어야 변경 감지가 동작한다. (빠뜨리면 예외 없이 저장만 되지 않는다)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateBookDetails(String isbn) {
        bookInfoRepository.findByIsbn(isbn)
            .filter(bookInfo -> !bookInfo.hasBookDetails())
            .ifPresent(this::applyBookDetails);
    }

    private void applyBookDetails(BookInfo bookInfo) {
        BookDetailResponse bookDetail = bookDetailLookupService.getBookDetailByIsbn(bookInfo.getIsbn(), true);
        bookInfo.applyBookDetailsIfAbsent(bookDetail.title(), bookDetail.author(), bookDetail.cover());
    }
}
