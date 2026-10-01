package com.bookwheel.server.admin.service;

import com.bookwheel.server.admin.entity.ModerationReport;
import com.bookwheel.server.admin.entity.ReportTargetType;
import com.bookwheel.server.admin.repository.ModerationReportRepository;
import com.bookwheel.server.community.entity.PostReport;
import com.bookwheel.server.community.entity.PostCommentReport;
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

    public void record(PostReport report) {
        User author = report.getPost().getUploader();
        repository.save(new ModerationReport(ReportTargetType.POST, report.getReportId(),
            report.getPost().getPostId(), report.getPost().getPostId(),
            author == null ? null : author.getId(), CommunityAuthorDisplay.displayName(author),
            report.getReporter().getId(), report.getReporter().getNickname(),
            report.getPost().getContent(), report.getReason(), LocalDateTime.now(clock)));
    }

    public void record(PostCommentReport report) {
        User author = report.getComment().getUser();
        repository.save(new ModerationReport(ReportTargetType.COMMENT, report.getReportId(),
            report.getComment().getPostCommentId(), report.getComment().getPost().getPostId(),
            author == null ? null : author.getId(), CommunityAuthorDisplay.displayName(author),
            report.getReporter().getId(), report.getReporter().getNickname(),
            report.getComment().getContent(), report.getReason(), LocalDateTime.now(clock)));
    }
}
