package com.bookwheel.server.admin.dto;

import com.bookwheel.server.admin.entity.*;
import com.bookwheel.server.community.dto.PostReportReason;
import java.time.LocalDateTime;

public record AdminReportResponse(
    Long reportId, ReportTargetType targetType, Long targetId, Long postId,
    String authorUserPK, String authorNickname, String reporterUserPK, String reporterNickname,
    String contentSnapshot, PostReportReason reason, LocalDateTime createdAt,
    ReportStatus status, ReportAction action, String processingReason,
    String processedByAdminPK, LocalDateTime processedAt, String banType
) {
    public static AdminReportResponse from(ModerationReport r) {
        return new AdminReportResponse(r.getReportId(), r.getTargetType(), r.getTargetId(), r.getPostId(),
            r.getAuthorUserPK(), r.getAuthorNickname(), r.getReporterUserPK(), r.getReporterNickname(),
            r.getContentSnapshot(), r.getReason(), r.getCreatedAt(), r.getStatus(), r.getAction(),
            r.getProcessingReason(), r.getProcessedByAdminPK(), r.getProcessedAt(), r.getBanType());
    }
}
