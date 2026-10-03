package com.bookwheel.server.admin.service;

import com.bookwheel.server.admin.dto.*;
import com.bookwheel.server.admin.entity.*;
import com.bookwheel.server.admin.repository.*;
import com.bookwheel.server.common.exception.*;
import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.community.dto.PostReportReason;
import com.bookwheel.server.community.entity.*;
import com.bookwheel.server.community.repository.*;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminReportServiceTest {
    @Mock ModerationReportRepository reports;
    @Mock AdminRepository admins;
    @Mock UserRepository users;
    @Mock PostRepository posts;
    @Mock PostCommentRepository comments;
    @Mock AdminService adminService;
    @Mock S3Service s3Service;
    @Spy Clock clock = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC);
    @InjectMocks AdminReportService service;
    private final User author = User.builder().nickname("author").build();

    private void admin() {
        when(admins.findById("admin")).thenReturn(Optional.of(Admin.builder().name("admin").build()));
    }

    private ModerationReport report(ReportTargetType type) {
        return ModerationReport.builder()
            .targetType(type)
            .targetId(type == ReportTargetType.POST ? 10L : 20L)
            .postId(10L)
            .authorUserPK(author.getId())
            .authorNickname("author")
            .reporterUserPK("reporter-pk")
            .reporterNickname("reporter")
            .contentSnapshot("evidence")
            .reason(PostReportReason.ABUSE)
            .createdAt(LocalDateTime.now(clock))
            .build();
    }

    private ModerationReport prepare(ReportTargetType type, boolean exists) {
        return prepare(type, exists, "reporter-pk");
    }

    private ModerationReport prepare(ReportTargetType type, boolean exists, String reporterUserPK) {
        admin();
        ModerationReport report = report(type);
        var target = mock(ModerationReportRepository.ProcessingTarget.class);
        when(target.getAuthorUserPK()).thenReturn(author.getId());
        when(target.getReporterUserPK()).thenReturn(reporterUserPK);
        when(target.getPostId()).thenReturn(10L);
        lenient().when(target.getTargetType()).thenReturn(type);
        when(reports.findProcessingTarget(1L)).thenReturn(Optional.of(target));
        when(users.findByUserPKForUpdate(author.getId())).thenReturn(Optional.of(author));
        if (reporterUserPK != null && !reporterUserPK.equals(author.getId())) {
            when(users.findByUserPKForUpdate(reporterUserPK)).thenReturn(Optional.empty());
        }
        when(posts.findByPostIdForUpdate(10L)).thenReturn(exists
            ? Optional.of(Post.builder().postId(10L).uploader(author).build()) : Optional.empty());
        if (exists && type == ReportTargetType.COMMENT) {
            when(target.getTargetId()).thenReturn(20L);
            when(comments.findForReport(20L, 10L)).thenReturn(Optional.of(PostComment.builder().postCommentId(20L).build()));
        }
        when(reports.findForUpdate(1L)).thenReturn(Optional.of(report));
        return report;
    }

    @Test void dismissesDeletedTargetAndKeepsEvidence() {
        ModerationReport report = prepare(ReportTargetType.COMMENT, false);
        var response = service.process("admin", 1L, new ReportProcessRequest("DISMISS", "근거 부족", null));
        assertThat(response.status()).isEqualTo(ReportStatus.DISMISSED);
        assertThat(response.contentSnapshot()).isEqualTo("evidence");
        assertThat(report.getProcessedByAdminPK()).isEqualTo("admin");
        assertThat(report.getProcessedAt()).isEqualTo(LocalDateTime.now(clock));
        verifyNoInteractions(adminService);
        verify(comments, never()).delete(any());
    }

    @Test void deletesCommentWhilePreservingReport() {
        ModerationReport report = prepare(ReportTargetType.COMMENT, true);
        service.process("admin", 1L, new ReportProcessRequest("DELETE_CONTENT", "욕설", null));
        assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        verify(comments).delete(any(PostComment.class));
        verify(reports, never()).delete(any());
        var order = inOrder(users, posts, comments, reports);
        order.verify(users).findByUserPKForUpdate(author.getId());
        order.verify(posts).findByPostIdForUpdate(10L);
        order.verify(comments).findForReport(20L, 10L);
        order.verify(reports).findForUpdate(1L);
        order.verify(comments).delete(any());
    }

    @Test void deletesPostThroughExistingDeletionServiceAndBansAuthor() {
        ModerationReport report = prepare(ReportTargetType.POST, true);
        service.process("admin", 1L, new ReportProcessRequest("DELETE_AND_BAN", " 스팸 반복 ", "THREE_DAYS"));
        verify(adminService).banUser(author.getId(), new AdminBanRequest("THREE_DAYS", BanReason.ETC, "스팸 반복"));
        verify(adminService).deletePost(10L, new AdminPostDeleteRequest(PostDeletionReason.OTHER));
        assertThat(report.getProcessingReason()).isEqualTo("스팸 반복");
        assertThat(report.getBanType()).isEqualTo("THREE_DAYS");
    }

    @Test void secondProcessingDoesNotRepeatSideEffects() {
        ModerationReport report = prepare(ReportTargetType.POST, true);
        report.process(ReportAction.DELETE_CONTENT, "done", "other-admin", LocalDateTime.now(clock), null);
        error(() -> service.process("admin", 1L, new ReportProcessRequest("DELETE_AND_BAN", "again", "PERMANENT")),
            ErrorCode.ALREADY_PROCESSED_REPORT);
        verifyNoInteractions(adminService);
    }

    @ParameterizedTest
    @CsvSource({"a,z", "z,a", "same,same"})
    void locksBothUsersInStableOrderBeforeContentAndReport(String authorUserPK, String reporterUserPK) {
        ReflectionTestUtils.setField(author, "id", authorUserPK);
        prepare(ReportTargetType.POST, true, reporterUserPK);

        service.process("admin", 1L, new ReportProcessRequest("DELETE_CONTENT", "delete", null));

        var order = inOrder(users, posts, reports, adminService);
        for (String userPK : new TreeSet<>(List.of(authorUserPK, reporterUserPK))) {
            order.verify(users).findByUserPKForUpdate(userPK);
        }
        order.verify(posts).findByPostIdForUpdate(10L);
        order.verify(reports).findForUpdate(1L);
        order.verify(adminService).deletePost(10L, new AdminPostDeleteRequest(PostDeletionReason.OTHER));
        verify(users, times(authorUserPK.equals(reporterUserPK) ? 1 : 2)).findByUserPKForUpdate(anyString());
    }

    @Test void reporterLockFailureStopsBeforeAcquiringContentOrReportLocks() {
        admin();
        var target = mock(ModerationReportRepository.ProcessingTarget.class);
        when(target.getAuthorUserPK()).thenReturn("a-author");
        when(target.getReporterUserPK()).thenReturn("z-reporter");
        when(reports.findProcessingTarget(1L)).thenReturn(Optional.of(target));
        when(users.findByUserPKForUpdate("a-author")).thenReturn(Optional.of(author));
        when(users.findByUserPKForUpdate("z-reporter"))
            .thenThrow(new PessimisticLockingFailureException("reporter is being purged"));

        assertThatThrownBy(() -> service.process("admin", 1L,
            new ReportProcessRequest("DELETE_CONTENT", "delete", null)))
            .isInstanceOf(PessimisticLockingFailureException.class);

        verifyNoInteractions(posts, comments, adminService);
        verify(reports, never()).findForUpdate(anyLong());
    }

    @Test void anonymizedUsersDoNotPreventDismissingRetainedReport() {
        admin();
        var target = mock(ModerationReportRepository.ProcessingTarget.class);
        when(target.getPostId()).thenReturn(10L);
        when(reports.findProcessingTarget(1L)).thenReturn(Optional.of(target));
        ModerationReport report = report(ReportTargetType.POST);
        ReflectionTestUtils.setField(report, "authorUserPK", null);
        ReflectionTestUtils.setField(report, "reporterUserPK", null);
        when(reports.findForUpdate(1L)).thenReturn(Optional.of(report));

        var response = service.process("admin", 1L, new ReportProcessRequest("DISMISS", "dismiss", null));

        assertThat(response.status()).isEqualTo(ReportStatus.DISMISSED);
        verifyNoInteractions(users, adminService);
    }

    @Test void deletingMissingTargetLeavesReportPending() {
        ModerationReport report = prepare(ReportTargetType.COMMENT, false);
        error(() -> service.process("admin", 1L, new ReportProcessRequest("DELETE_CONTENT", "delete", null)),
            ErrorCode.REPORT_TARGET_DELETED);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.PENDING);
        verifyNoInteractions(adminService);
    }

    @Test void banningWithdrawnAuthorIsRejected() {
        ModerationReport report = prepare(ReportTargetType.POST, false);
        author.deactivate(LocalDateTime.now(clock).minusDays(31), LocalDateTime.now(clock).minusDays(1));
        error(() -> service.process("admin", 1L, new ReportProcessRequest("BAN_USER", "ban", "PERMANENT")),
            ErrorCode.REPORT_AUTHOR_UNAVAILABLE);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.PENDING);
        verifyNoInteractions(adminService);
    }

    @Test void alreadyBannedAuthorIsNotBannedAgainByAnotherReport() {
        prepare(ReportTargetType.POST, false);
        author.applyBan("SEVEN_DAYS", LocalDateTime.now(clock));
        error(() -> service.process("admin", 1L, new ReportProcessRequest("BAN_USER", "ban", "THREE_DAYS")),
            ErrorCode.ALREADY_BANNED_USER);
        verifyNoInteractions(adminService);
    }

    @Test void canBanActiveAuthorAfterContentWasDeleted() {
        prepare(ReportTargetType.POST, false);
        var response = service.process("admin", 1L, new ReportProcessRequest("BAN_USER", "ban", "SEVEN_DAYS"));
        assertThat(response.status()).isEqualTo(ReportStatus.RESOLVED);
        verify(adminService).banUser(author.getId(), new AdminBanRequest("SEVEN_DAYS", BanReason.ETC, "ban"));
        verify(adminService, never()).deletePost(any(), any());
    }

    @Test void validatesActionAndBanTypeCombinationBeforeLocking() {
        admin();
        error(() -> service.process("admin", 1L, new ReportProcessRequest("BAN_USER", "ban", null)), ErrorCode.INVALID_INPUT_VALUE);
        error(() -> service.process("admin", 1L, new ReportProcessRequest("DISMISS", "dismiss", "PERMANENT")), ErrorCode.INVALID_INPUT_VALUE);
        verifyNoInteractions(reports);
    }

    @Test void missingReportIsRejected() {
        admin();
        error(() -> service.process("admin", 1L, new ReportProcessRequest("DISMISS", "dismiss", null)), ErrorCode.REPORT_NOT_FOUND);
        verifyNoInteractions(adminService);
    }

    @Test void listUsesSnapshotWithoutPerRowContentOrUserQueries() {
        admin();
        ModerationReport snapshot = report(ReportTargetType.COMMENT);
        when(reports.findReports(ReportTargetType.COMMENT, ReportStatus.PENDING, PageRequest.of(0, 20)))
            .thenReturn(new PageImpl<>(List.of(snapshot)));
        var response = service.list("admin", ReportTargetType.COMMENT, ReportStatus.PENDING, 0, 20);
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).targetType()).isEqualTo(ReportTargetType.COMMENT);
        verifyNoInteractions(users, posts, comments, s3Service);
    }

    @ParameterizedTest @CsvSource({"-1,20", "0,0", "0,51", "2147483647,20"})
    void rejectsInvalidPagination(int page, int size) {
        admin();
        error(() -> service.list("admin", null, null, page, size), ErrorCode.INVALID_INPUT_VALUE);
        verifyNoInteractions(reports);
    }

    @Test void detailRemainsAvailableAfterDeletion() {
        admin();
        ModerationReport snapshot = report(ReportTargetType.COMMENT);
        when(reports.findById(1L)).thenReturn(Optional.of(snapshot));
        var response = service.detail("admin", 1L);
        assertThat(response.targetExists()).isFalse();
        assertThat(response.authorActive()).isFalse();
        assertThat(response.report().contentSnapshot()).isEqualTo("evidence");
        assertThat(response.imageUrls()).isEmpty();
    }

    @Test void rejectsInactiveAdministrator() {
        when(admins.findById("admin")).thenReturn(Optional.of(Admin.builder().isActive(false).build()));
        error(() -> service.list("admin", null, null, 0, 20), ErrorCode.INACTIVE_ADMIN);
        verifyNoInteractions(reports);
    }

    private void error(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
