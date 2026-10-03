package com.bookwheel.server.admin.entity;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.dto.PostReportReason;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "moderation_report", uniqueConstraints = {
    @UniqueConstraint(name = "uk_moderation_source", columnNames = {"target_type", "source_report_id"}),
    @UniqueConstraint(name = "uk_moderation_target_reporter", columnNames = {"target_type", "target_id", "reporter_user_pk"})
}, indexes = {
    @Index(name = "idx_moderation_queue", columnList = "status,target_type,created_at,report_id"),
    @Index(name = "idx_moderation_author", columnList = "author_user_pk"),
    @Index(name = "idx_moderation_reporter", columnList = "reporter_user_pk")
})
public class ModerationReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reportId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ReportTargetType targetType;
    @Column private Long sourceReportId;
    @Column(nullable = false) private Long targetId;
    @Column(nullable = false) private Long postId;
    @Column(name = "author_user_pk", length = 50) private String authorUserPK;
    @Column(length = 50) private String authorNickname;
    @Column(name = "reporter_user_pk", length = 50) private String reporterUserPK;
    @Column(length = 50) private String reporterNickname;
    @Column(nullable = false, columnDefinition = "TEXT") private String contentSnapshot;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private PostReportReason reason;
    private LocalDateTime createdAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ReportStatus status = ReportStatus.PENDING;
    @Enumerated(EnumType.STRING) @Column(length = 30) private ReportAction action;
    @Column(name = "processed_by_admin_pk", length = 50) private String processedByAdminPK;
    private LocalDateTime processedAt;
    @Column(length = 255) private String processingReason;
    @Column(length = 20) private String banType;

    @Builder
    private ModerationReport(ReportTargetType targetType, Long targetId, Long postId,
            String authorUserPK, String authorNickname, String reporterUserPK, String reporterNickname,
            String contentSnapshot, PostReportReason reason, LocalDateTime createdAt) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.postId = postId;
        this.authorUserPK = authorUserPK;
        this.authorNickname = authorNickname;
        this.reporterUserPK = reporterUserPK;
        this.reporterNickname = reporterNickname;
        this.contentSnapshot = contentSnapshot;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public void requirePending() {
        if (status != ReportStatus.PENDING) throw new BusinessException(ErrorCode.ALREADY_PROCESSED_REPORT);
    }

    public void process(ReportAction action, String reason, String adminPK, LocalDateTime now, String banType) {
        requirePending();
        this.action = action;
        this.status = action == ReportAction.DISMISS ? ReportStatus.DISMISSED : ReportStatus.RESOLVED;
        this.processingReason = reason;
        this.processedByAdminPK = adminPK;
        this.processedAt = now;
        this.banType = banType;
    }
}
