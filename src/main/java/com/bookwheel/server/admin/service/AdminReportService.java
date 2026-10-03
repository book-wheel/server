package com.bookwheel.server.admin.service;

import com.bookwheel.server.admin.dto.*;
import com.bookwheel.server.admin.entity.*;
import com.bookwheel.server.admin.repository.AdminRepository;
import com.bookwheel.server.admin.repository.ModerationReportRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.entity.PostComment;
import com.bookwheel.server.community.repository.PostRepository;
import com.bookwheel.server.community.repository.PostCommentRepository;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReportService {
    private final ModerationReportRepository reports;
    private final AdminRepository admins;
    private final UserRepository users;
    private final PostRepository posts;
    private final PostCommentRepository comments;
    private final AdminService adminService;
    private final S3Service s3Service;
    private final Clock clock;

    public Page<AdminReportResponse> list(String adminPK, ReportTargetType type, ReportStatus status, int page, int size) {
        requireAdmin(adminPK);
        if (page < 0 || size < 1 || size > 50 || (long) page * size > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return reports.findReports(type, status, PageRequest.of(page, size)).map(AdminReportResponse::from);
    }

    public AdminReportDetailResponse detail(String adminPK, Long reportId) {
        requireAdmin(adminPK);
        ModerationReport report = reports.findById(reportId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        Post post = posts.findById(report.getPostId()).orElse(null);
        boolean exists = post != null && (report.getTargetType() == ReportTargetType.POST
            || comments.findByPostCommentIdAndPost_PostId(report.getTargetId(), report.getPostId()).isPresent());
        boolean authorActive = report.getAuthorUserPK() != null && users.findById(report.getAuthorUserPK())
            .map(user -> Boolean.TRUE.equals(user.getIsActive())).orElse(false);
        List<String> images = exists && report.getTargetType() == ReportTargetType.POST
            ? post.getImages().stream().map(image -> s3Service.getPresignedGetUrl(image.getObjectKey())).toList()
            : List.of();
        return new AdminReportDetailResponse(AdminReportResponse.from(report), exists, authorActive, images);
    }

    @Transactional
    public AdminReportResponse process(String adminPK, Long reportId, ReportProcessRequest request) {
        requireAdmin(adminPK);
        ReportAction action = request.reportAction();
        if (action.bansUser() != (request.banType() != null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        ModerationReportRepository.ProcessingTarget target = reports.findProcessingTarget(reportId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        User author = lockReportUsers(target);
        Post post = posts.findByPostIdForUpdate(target.getPostId()).orElse(null);
        PostComment comment = post != null && target.getTargetType() == ReportTargetType.COMMENT
            ? comments.findForReport(target.getTargetId(), target.getPostId()).orElse(null) : null;
        ModerationReport report = reports.findForUpdate(reportId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
        report.requirePending();

        if (action.deletesContent() && (post == null || (target.getTargetType() == ReportTargetType.COMMENT && comment == null))) {
            throw new BusinessException(ErrorCode.REPORT_TARGET_DELETED);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (action.bansUser()) {
            if (author == null || !Boolean.TRUE.equals(author.getIsActive()) || report.getAuthorUserPK() == null) {
                throw new BusinessException(ErrorCode.REPORT_AUTHOR_UNAVAILABLE);
            }
            if (!"ACTIVE".equals(author.getBanStatus(now))) {
                throw new BusinessException(ErrorCode.ALREADY_BANNED_USER);
            }
            adminService.banUser(author.getId(), new AdminBanRequest(request.banType(), BanReason.ETC, request.reason().strip()));
        }
        if (action.deletesContent()) {
            if (target.getTargetType() == ReportTargetType.POST) {
                adminService.deletePost(post.getPostId(), new AdminPostDeleteRequest(PostDeletionReason.OTHER));
            } else {
                comments.delete(comment);
            }
        }
        report.process(action, request.reason().strip(), adminPK, now, request.banType());
        return AdminReportResponse.from(report);
    }

    private User lockReportUsers(ModerationReportRepository.ProcessingTarget target) {
        User author = null;
        // 영구 삭제가 잡는 사용자 잠금을 먼저 확보하고, 두 계정은 항상 같은 순서로 잠근다.
        for (String userPK : Stream.of(target.getAuthorUserPK(), target.getReporterUserPK())
                .filter(Objects::nonNull).distinct().sorted().toList()) {
            User user = users.findByUserPKForUpdate(userPK).orElse(null);
            if (userPK.equals(target.getAuthorUserPK())) {
                author = user;
            }
        }
        return author;
    }

    private void requireAdmin(String adminPK) {
        Admin admin = admins.findById(adminPK)
            .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_NOT_FOUND));
        if (!Boolean.TRUE.equals(admin.getIsActive())) throw new BusinessException(ErrorCode.INACTIVE_ADMIN);
    }
}
