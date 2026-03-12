package com.bookwheel.server.community.repository;

import com.bookwheel.server.book.entity.Book;
import com.bookwheel.server.community.entity.BookInfo;
import com.bookwheel.server.community.entity.BookReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookReviewRepository extends JpaRepository<BookReview, Long> {
    boolean existsByBookInfoAndReviewer_UserId(BookInfo bookInfo, String userId);
    long countByBookInfoAndIsRecommended(BookInfo bookInfo, boolean isRecommended);

    List<BookReview> findAllByBookInfoOrderByCreatedAtDesc(BookInfo bookInfo);
}
