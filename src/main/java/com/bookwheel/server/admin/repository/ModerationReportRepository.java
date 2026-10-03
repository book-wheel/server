package com.bookwheel.server.admin.repository;

import com.bookwheel.server.admin.entity.ModerationReport;
import com.bookwheel.server.admin.entity.ReportTargetType;
import com.bookwheel.server.admin.entity.ReportStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ModerationReportRepository extends JpaRepository<ModerationReport, Long> {
    boolean existsByTargetTypeAndTargetIdAndReporterUserPK(ReportTargetType targetType,
        Long targetId, String reporterUserPK);

    @Query("""
        select r from ModerationReport r
        where (:type is null or r.targetType = :type) and (:status is null or r.status = :status)
        order by r.createdAt desc, r.reportId desc
        """)
    Page<ModerationReport> findReports(@Param("type") ReportTargetType type,
        @Param("status") ReportStatus status, Pageable pageable);

    interface ProcessingTarget {
        Long getPostId();
        Long getTargetId();
        ReportTargetType getTargetType();
        String getAuthorUserPK();
        String getReporterUserPK();
    }

    @Query("""
        select r.postId as postId, r.targetId as targetId, r.targetType as targetType,
            r.authorUserPK as authorUserPK, r.reporterUserPK as reporterUserPK
        from ModerationReport r where r.reportId = :reportId
        """)
    Optional<ProcessingTarget> findProcessingTarget(@Param("reportId") Long reportId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ModerationReport r where r.reportId = :reportId")
    Optional<ModerationReport> findForUpdate(@Param("reportId") Long reportId);
}
