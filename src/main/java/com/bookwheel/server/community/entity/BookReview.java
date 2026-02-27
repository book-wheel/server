package com.bookwheel.server.community.entity;

import com.bookwheel.server.book.entity.Book;
import com.bookwheel.server.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@Table(name = "book_review")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class BookReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long reviewId;

    // 1. 어떤 책에 작성된 리뷰인지 (Book 참조)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", referencedColumnName = "book_id", nullable = false)
    private Book book;

    // 2. 누가 작성한 리뷰인지 (User 참조)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User reviewer;

    // 3. 리뷰 내용
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // 4. 별점 (1~5점)
    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "is_hidden", nullable = false)
    private Boolean isHidden;
}
