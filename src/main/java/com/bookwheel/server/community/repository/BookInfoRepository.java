package com.bookwheel.server.community.repository;

import com.bookwheel.server.community.entity.BookInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BookInfoRepository extends JpaRepository<BookInfo, Long> {
    Optional<BookInfo> findByIsbn(String isbn);

    /**
     * 같은 ISBN 의 BookInfo 는 게시글·리뷰·투표·찜이 함께 쓰므로, 없으면 만들어서 돌려준다.
     *
     * 조회와 저장 사이에 같은 ISBN 이 동시에 등록될 수 있다.
     * save() 로 넣으면 이때 isbn 유니크 제약을 위반해 트랜잭션이 통째로 실패하므로,
     * 충돌 시 아무것도 하지 않는 업서트로 넣고 먼저 저장된 행을 다시 읽는다.
     */
    default BookInfo findOrCreateByIsbn(String isbn) {
        return findByIsbn(isbn).orElseGet(() -> {
            insertIfAbsent(isbn);
            return findByIsbn(isbn)
                .orElseThrow(() -> new IllegalStateException("BookInfo 생성 직후 조회에 실패했습니다. ISBN: " + isbn));
        });
    }

    // 업서트가 no-op 으로 동작하려면 book_info.isbn 에 유니크 인덱스가 있어야 한다.
    // (db/migration_book_info_isbn_unique.sql 참고)
    @Modifying(flushAutomatically = true)
    @Query(
        value = "insert into book_info (isbn) values (:isbn) on duplicate key update isbn = isbn",
        nativeQuery = true
    )
    void insertIfAbsent(@Param("isbn") String isbn);
}
