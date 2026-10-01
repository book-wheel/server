package com.bookwheel.server.admin.service;

import com.bookwheel.server.admin.entity.*;
import com.bookwheel.server.admin.repository.ModerationReportRepository;
import com.bookwheel.server.community.dto.PostReportReason;
import com.bookwheel.server.community.entity.*;
import com.bookwheel.server.user.entity.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportRecordingServiceTest {
    private final ModerationReportRepository repository = mock(ModerationReportRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC);
    private final ReportRecordingService service = new ReportRecordingService(repository, clock);
    private final User author = User.builder().nickname("author").build();
    private final User reporter = User.builder().nickname("reporter").build();

    @Test void sourceIdsMayOverlapWhileTargetTypesRemainDistinct() {
        Post post = Post.builder().postId(10L).content("post evidence").uploader(author).build();
        PostReport postReport = new PostReport(post, reporter, PostReportReason.SPAM);
        ReflectionTestUtils.setField(postReport, "reportId", 1L);
        PostComment comment = PostComment.builder().post(post).postCommentId(20L).content("comment evidence").user(author).build();
        PostCommentReport commentReport = new PostCommentReport(comment, reporter, PostReportReason.ABUSE);
        ReflectionTestUtils.setField(commentReport, "reportId", 1L);
        service.record(postReport);
        service.record(commentReport);
        var saved = ArgumentCaptor.forClass(ModerationReport.class);
        verify(repository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(ModerationReport::getSourceReportId).containsExactly(1L, 1L);
        assertThat(saved.getAllValues()).extracting(ModerationReport::getTargetType)
            .containsExactly(ReportTargetType.POST, ReportTargetType.COMMENT);
        assertThat(saved.getAllValues()).extracting(ModerationReport::getContentSnapshot)
            .containsExactly("post evidence", "comment evidence");
        assertThat(saved.getValue().getAuthorUserPK()).isEqualTo(author.getId());
        assertThat(saved.getValue().getReporterUserPK()).isEqualTo(reporter.getId());
        assertThat(saved.getValue().getStatus()).isEqualTo(ReportStatus.PENDING);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(LocalDateTime.now(clock));
    }

    @Test void deletedAuthorCanStillHaveContentReported() {
        Post post = Post.builder().postId(10L).content("retained").build();
        service.record(new PostReport(post, reporter, PostReportReason.SPAM));
        verify(repository).save(argThat(report -> report.getAuthorUserPK() == null
            && report.getAuthorNickname().equals("탈퇴한 사용자") && report.getContentSnapshot().equals("retained")));
    }
}
