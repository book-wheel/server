package com.bookwheel.server.community.entity;

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
    name = "book_vote",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_book_vote_book_user",
        columnNames = {"book_info_id", "user_id"}
    )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class BookVote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vote_id")
    private Long voteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_info_id", nullable = false)
    private BookInfo bookInfo;

    // 누가 투표했는지 (User 참조)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    // 추천/비추천 (true: 추천, false: 비추천)
    @Column(name = "is_recommended", nullable = false)
    private Boolean isRecommended;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public static BookVote create(BookInfo bookInfo, User user, boolean isRecommended) {
        return BookVote.builder()
            .bookInfo(bookInfo)
            .user(user)
            .isRecommended(isRecommended)
            .build();
    }

    // 기존 투표의 추천/비추천 값을 변경한다.
    public void changeVote(boolean isRecommended) {
        this.isRecommended = isRecommended;
    }
}
