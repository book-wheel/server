package com.bookwheel.server.community.entity;

import com.bookwheel.server.community.dto.PostReportReason;
import com.bookwheel.server.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Post post; // 신고 대상 사진

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private User reporter; // 신고자

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostReportReason reason; //신고 사유

    public PostReport(Post post, User reporter, PostReportReason reason) {
        this.post = post;
        this.reporter = reporter;
        this.reason = reason;
    }
}
