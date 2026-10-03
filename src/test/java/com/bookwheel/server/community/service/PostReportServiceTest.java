package com.bookwheel.server.community.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.admin.service.ReportRecordingService;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.dto.*;
import com.bookwheel.server.community.entity.*;
import com.bookwheel.server.community.repository.*;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostReportServiceTest {
    @Mock ReportRecordingService reportRecordingService;
    @Mock UserRepository userRepository;
    @Mock PostRepository postRepository;
    @Mock PostCommentRepository postCommentRepository;
    @InjectMocks PostService service;

    private final User reporter = User.builder().nickname("reporter").build();
    private final User author = User.builder().nickname("author").build();
    private final PostReportRequest postRequest = new PostReportRequest(PostReportReason.SPAM);
    private final PostCommentReportRequest commentRequest = new PostCommentReportRequest(PostReportReason.ABUSE);

    private void stubReporter() {
        when(userRepository.findByUserPKForUpdate(reporter.getId())).thenReturn(Optional.of(reporter));
    }

    private Post stubPost(User uploader) {
        stubReporter();
        Post post = Post.builder().postId(1L).uploader(uploader).build();
        when(postRepository.findByPostIdForUpdate(1L)).thenReturn(Optional.of(post));
        return post;
    }

    private PostComment stubComment(User commentAuthor) {
        Post post = stubPost(author);
        PostComment comment = PostComment.builder().postCommentId(2L).post(post).user(commentAuthor).build();
        when(postCommentRepository.findForReport(2L, 1L)).thenReturn(Optional.of(comment));
        return comment;
    }

    @Test void reportsPostWithReasonAndReporter() {
        Post post = stubPost(author);
        service.reportPost(1L, postRequest, reporter.getId());
        verify(reportRecordingService).record(post, reporter, PostReportReason.SPAM);
    }

    @Test void rejectsOwnPost() {
        stubPost(reporter);
        error(() -> service.reportPost(1L, postRequest, reporter.getId()), ErrorCode.CANNOT_REPORT_OWN_POST);
        verifyNoInteractions(reportRecordingService);
    }

    @Test void rejectsDuplicatePostReport() {
        Post post = stubPost(author);
        doThrow(new BusinessException(ErrorCode.ALREADY_REPORTED))
            .when(reportRecordingService).record(post, reporter, PostReportReason.SPAM);
        error(() -> service.reportPost(1L, postRequest, reporter.getId()), ErrorCode.ALREADY_REPORTED);

    }

    @Test void reportsPostWithDeletedAuthor() {
        stubPost(null);
        service.reportPost(1L, postRequest, reporter.getId());
        verify(reportRecordingService).record(any(Post.class), eq(reporter), eq(PostReportReason.SPAM));
    }

    @Test void reportsCommentUsingUserPostCommentLockOrder() {
        PostComment comment = stubComment(author);
        service.reportPostComment(1L, 2L, commentRequest, reporter.getId());
        var order = inOrder(userRepository, postRepository, postCommentRepository, reportRecordingService);
        order.verify(userRepository).findByUserPKForUpdate(reporter.getId());
        order.verify(postRepository).findByPostIdForUpdate(1L);
        order.verify(postCommentRepository).findForReport(2L, 1L);
        order.verify(reportRecordingService).record(comment, reporter, PostReportReason.ABUSE);
    }

    @Test void rejectsOwnComment() {
        stubComment(reporter);
        error(() -> service.reportPostComment(1L, 2L, commentRequest, reporter.getId()), ErrorCode.CANNOT_REPORT_OWN_COMMENT);
        verifyNoInteractions(reportRecordingService);
    }

    @Test void rejectsDuplicateCommentReport() {
        PostComment comment = stubComment(author);
        doThrow(new BusinessException(ErrorCode.COMMENT_ALREADY_REPORTED))
            .when(reportRecordingService).record(comment, reporter, PostReportReason.ABUSE);
        error(() -> service.reportPostComment(1L, 2L, commentRequest, reporter.getId()), ErrorCode.COMMENT_ALREADY_REPORTED);

    }

    @Test void reportsCommentWithDeletedAuthor() {
        stubComment(null);
        service.reportPostComment(1L, 2L, commentRequest, reporter.getId());
        verify(reportRecordingService).record(any(PostComment.class), eq(reporter), eq(PostReportReason.ABUSE));
    }

    @Test void rejectsMissingPostForBothReportTypes() {
        stubReporter();
        error(() -> service.reportPost(1L, postRequest, reporter.getId()), ErrorCode.POST_NOT_FOUND);
        error(() -> service.reportPostComment(1L, 2L, commentRequest, reporter.getId()), ErrorCode.POST_NOT_FOUND);
        verifyNoInteractions(postCommentRepository, reportRecordingService);
    }

    @Test void rejectsCommentOutsideRequestedPostOrMissingComment() {
        stubPost(author);
        error(() -> service.reportPostComment(1L, 99L, commentRequest, reporter.getId()), ErrorCode.POST_COMMENT_NOT_FOUND);
        verify(postCommentRepository).findForReport(99L, 1L);
        verifyNoInteractions(reportRecordingService);
    }

    @Test void rejectsMissingReporter() {
        error(() -> service.reportPost(1L, postRequest, "missing"), ErrorCode.USER_NOT_FOUND);
        error(() -> service.reportPostComment(1L, 2L, commentRequest, "missing"), ErrorCode.USER_NOT_FOUND);
        verifyNoInteractions(postRepository, reportRecordingService);
    }

    @Test void rejectsInactiveReporter() {
        User inactive = User.builder().isActive(false).build();
        when(userRepository.findByUserPKForUpdate(inactive.getId())).thenReturn(Optional.of(inactive));
        error(() -> service.reportPost(1L, postRequest, inactive.getId()), ErrorCode.INACTIVE_USER);
        error(() -> service.reportPostComment(1L, 2L, commentRequest, inactive.getId()), ErrorCode.INACTIVE_USER);
        verifyNoInteractions(postRepository, reportRecordingService);
    }

    private void error(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
