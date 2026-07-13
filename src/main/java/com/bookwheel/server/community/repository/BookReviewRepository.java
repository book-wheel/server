package com.bookwheel.server.community.repository;

import com.bookwheel.server.book.entity.Book;
import com.bookwheel.server.community.entity.BookInfo;
import com.bookwheel.server.community.entity.BookReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookReviewRepository extends JpaRepository<BookReview, Long> {
    boolean existsByBookInfoAndReviewer_Id(BookInfo bookInfo, String userPK);
    long countByBookInfoAndIsRecommended(BookInfo bookInfo, boolean isRecommended);

    Optional<BookReview> findByBookInfoAndReviewer_Id(BookInfo bookInfo, String userPK);

    Page<BookReview> findAllByBookInfo(BookInfo bookInfo, Pageable pageable);
}
