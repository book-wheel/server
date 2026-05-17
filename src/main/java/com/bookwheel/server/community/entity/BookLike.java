package com.bookwheel.server.community.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(
    name = "book_like",
    uniqueConstraints = @UniqueConstraint(columnNames = {"book_info_id", "user_pk"})
)
public class BookLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_like_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_info_id", nullable = false)
    private BookInfo bookInfo;

    @Column(name = "user_pk", nullable = false, length = 50)
    private String userPK;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public BookLike(BookInfo bookInfo, String userPK) {
        this.bookInfo = bookInfo;
        this.userPK = userPK;
    }

    public static BookLike create(BookInfo bookInfo, String userPK) {
        return BookLike.builder()
            .bookInfo(bookInfo)
            .userPK(userPK)
            .build();
    }
}
