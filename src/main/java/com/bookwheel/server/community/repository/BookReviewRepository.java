package com.bookwheel.server.community.repository;

import com.bookwheel.server.book.entity.Book;
import com.bookwheel.server.community.entity.BookReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookReviewRepository extends JpaRepository<BookReview, Long> {
    long countByBookAndIsRecommended(Book book, Boolean isRecommended);
    boolean existsByBookAndReviewer_UserId(Book book, String userId);

    List<BookReview> findAllByBookOrderByCreatedAtDesc(Book book);
}
