package com.bookwheel.server.community.repository;

import com.bookwheel.server.book.entity.Book;
import com.bookwheel.server.community.entity.BookInfo;
import com.bookwheel.server.community.entity.BookReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookReviewRepository extends JpaRepository<BookReview, Long> {
    boolean existsByBookInfoAndReviewer_Id(BookInfo bookInfo, String userPK);

    // 작성자(reviewer)를 fetch join으로 함께 로딩해 목록 매핑 시 리뷰어 조회 N+1을 방지한다.
    @Query(value = "select r from BookReview r join fetch r.reviewer where r.bookInfo = :bookInfo",
        countQuery = "select count(r) from BookReview r where r.bookInfo = :bookInfo")
    Page<BookReview> findAllByBookInfo(@Param("bookInfo") BookInfo bookInfo, Pageable pageable);
}
