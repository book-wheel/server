package com.bookwheel.server.community.repository;

import com.bookwheel.server.community.entity.BookReview;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookReviewRepository extends JpaRepository<BookReview, Long> {
}
