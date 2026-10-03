package com.bookwheel.server.admin.service;

import com.bookwheel.server.admin.entity.*;
import com.bookwheel.server.admin.repository.ModerationReportRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.dto.PostReportReason;
import com.bookwheel.server.community.entity.*;
import com.bookwheel.server.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportRecordingServiceTest {
    private final ModerationReportRepository repository = mock(ModerationReportRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC);
    private final ReportRecordingService service = new ReportRecordingService(repository, clock);
    private final User author = User.builder().nickname("author").build();
    private final User reporter = User.builder().nickname("reporter").build();

    @Test void targetIdsMayOverlapWhileTargetTypesRemainDistinct() {
        Post post = Post.builder().postId(10L).content("post evidence").uploader(author).build();
        PostComment comment = PostComment.builder().post(post).postCommentId(10L).content("comment evidence").user(author).build();
        service.record(post, reporter, PostReportReason.SPAM);
        service.record(comment, reporter, PostReportReason.ABUSE);
        var saved = ArgumentCaptor.forClass(ModerationReport.class);
        verify(repository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(ModerationReport::getTargetId).containsExactly(10L, 10L);
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
        service.record(post, reporter, PostReportReason.SPAM);
        verify(repository).save(argThat(report -> report.getAuthorUserPK() == null
            && report.getAuthorNickname().equals("탈퇴한 사용자") && report.getContentSnapshot().equals("retained")));
    }

    @ParameterizedTest
    @EnumSource(ReportStatus.class)
    void duplicatePostRemainsRejectedAfterProcessing(ReportStatus status) {
        Post post = Post.builder().postId(10L).content("evidence").uploader(author).build();
        service.record(post, reporter, PostReportReason.SPAM);
        var saved = ArgumentCaptor.forClass(ModerationReport.class);
        verify(repository).save(saved.capture());
        if (status != ReportStatus.PENDING) {
            saved.getValue().process(status == ReportStatus.DISMISSED ? ReportAction.DISMISS : ReportAction.BAN_USER,
                "processed", "admin-pk", LocalDateTime.now(clock), null);
        }
        when(repository.existsByTargetTypeAndTargetIdAndReporterUserPK(
            ReportTargetType.POST, 10L, reporter.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.record(post, reporter, PostReportReason.ABUSE))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.ALREADY_REPORTED));
        verify(repository, times(1)).save(any());
        assertThat(saved.getValue().getReason()).isEqualTo(PostReportReason.SPAM);
        assertThat(saved.getValue().getStatus()).isEqualTo(status);
    }

    @Test void duplicateCommentDoesNotSaveAnotherReport() {
        PostComment comment = PostComment.builder().postCommentId(20L).build();
        when(repository.existsByTargetTypeAndTargetIdAndReporterUserPK(
            ReportTargetType.COMMENT, 20L, reporter.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.record(comment, reporter, PostReportReason.ABUSE))
            .isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.COMMENT_ALREADY_REPORTED));
        verify(repository, never()).save(any());
    }

    @Test void anotherReporterCanReportTheSameTarget() {
        Post post = Post.builder().postId(10L).content("evidence").uploader(author).build();
        User another = User.builder().nickname("another").build();
        when(repository.existsByTargetTypeAndTargetIdAndReporterUserPK(
            ReportTargetType.POST, 10L, reporter.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.record(post, reporter, PostReportReason.SPAM))
            .isInstanceOf(BusinessException.class);
        service.record(post, another, PostReportReason.SPAM);
        verify(repository).save(argThat(report -> report.getReporterUserPK().equals(another.getId())
            && report.getTargetId().equals(10L) && report.getSourceReportId() == null));
    }
}
