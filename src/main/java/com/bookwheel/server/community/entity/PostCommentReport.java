package com.bookwheel.server.community.entity;

import com.bookwheel.server.community.dto.PostReportReason;
import com.bookwheel.server.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "post_comment_report", uniqueConstraints = @UniqueConstraint(
    name = "uk_comment_report_reporter", columnNames = {"comment_id", "reporter_user_pk"}))
public class PostCommentReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reportId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PostComment comment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_user_pk", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PostReportReason reason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public PostCommentReport(PostComment comment, User reporter, PostReportReason reason) {
        this.comment = comment;
        this.reporter = reporter;
        this.reason = reason;
    }
}
