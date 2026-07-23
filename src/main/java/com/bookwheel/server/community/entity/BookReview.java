package com.bookwheel.server.community.entity;

import com.bookwheel.server.book.entity.Book;
import com.bookwheel.server.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@Table(
    name = "book_review",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_book_review_book_user",
        columnNames = {"book_info_id", "user_id"}
    )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class BookReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long reviewId;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_info_id", nullable = false)
    private BookInfo bookInfo;

    // 누가 작성한 리뷰인지 (User 참조)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User reviewer;

    // 리뷰 내용
    @Column(nullable = false, length = 500, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_hidden", nullable = false)
    private Boolean isHidden;


    @Column(name = "like_count", nullable = false)
    @Builder.Default
    private int likeCount = 0;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }
}
