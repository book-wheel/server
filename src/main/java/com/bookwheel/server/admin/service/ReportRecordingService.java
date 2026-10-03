package com.bookwheel.server.admin.service;

import com.bookwheel.server.admin.entity.ModerationReport;
import com.bookwheel.server.admin.entity.ReportTargetType;
import com.bookwheel.server.admin.repository.ModerationReportRepository;
import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.entity.PostComment;
import com.bookwheel.server.community.dto.PostReportReason;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.support.CommunityAuthorDisplay;
import com.bookwheel.server.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ReportRecordingService {
    private final ModerationReportRepository repository;
    private final Clock clock;

    public void record(Post post, User reporter, PostReportReason reason) {
        if (repository.existsByTargetTypeAndTargetIdAndReporterUserPK(
                ReportTargetType.POST, post.getPostId(), reporter.getId())) {
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }
        User author = post.getUploader();
        repository.save(ModerationReport.builder()
            .targetType(ReportTargetType.POST)
            .targetId(post.getPostId())
            .postId(post.getPostId())
            .authorUserPK(author == null ? null : author.getId())
            .authorNickname(CommunityAuthorDisplay.displayName(author))
            .reporterUserPK(reporter.getId())
            .reporterNickname(reporter.getNickname())
            .contentSnapshot(post.getContent())
            .reason(reason)
            .createdAt(LocalDateTime.now(clock))
            .build());
    }

    public void record(PostComment comment, User reporter, PostReportReason reason) {
        if (repository.existsByTargetTypeAndTargetIdAndReporterUserPK(
                ReportTargetType.COMMENT, comment.getPostCommentId(), reporter.getId())) {
            throw new BusinessException(ErrorCode.COMMENT_ALREADY_REPORTED);
        }
        User author = comment.getUser();
        repository.save(ModerationReport.builder()
            .targetType(ReportTargetType.COMMENT)
            .targetId(comment.getPostCommentId())
            .postId(comment.getPost().getPostId())
            .authorUserPK(author == null ? null : author.getId())
            .authorNickname(CommunityAuthorDisplay.displayName(author))
            .reporterUserPK(reporter.getId())
            .reporterNickname(reporter.getNickname())
            .contentSnapshot(comment.getContent())
            .reason(reason)
            .createdAt(LocalDateTime.now(clock))
            .build());
    }
}
