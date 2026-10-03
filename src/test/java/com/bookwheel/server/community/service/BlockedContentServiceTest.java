package com.bookwheel.server.community.service;

import com.bookwheel.server.common.cursor.CommentCursor;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.common.util.CursorUtils;
import com.bookwheel.server.community.entity.BookInfo;
import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.entity.PostComment;
import com.bookwheel.server.community.repository.PostCommentRepository;
import com.bookwheel.server.community.repository.PostLikeRepository;
import com.bookwheel.server.community.repository.PostRepository;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlockedContentServiceTest {
    @Mock PostRepository posts;
    @Mock PostCommentRepository comments;
    @Mock PostLikeRepository likes;
    @Mock UserRepository users;
    @Mock S3Service s3;
    @Spy CursorUtils cursors = new CursorUtils(new ObjectMapper().findAndRegisterModules());
    @InjectMocks PostService service;

    private final String userPK = "viewer-pk";
    private final Post post = Post.builder().postId(10L).content("content")
            .bookInfo(BookInfo.builder().isbn("9780132350884").build()).build();
    private final LocalDateTime time = LocalDateTime.of(2026, 10, 3, 12, 0);

    @Test
    void hiddenPostCannotBeReadThroughDetailOrComments() {
        for (org.assertj.core.api.ThrowableAssert.ThrowingCallable request : List.<org.assertj.core.api.ThrowableAssert.ThrowingCallable>of(
                () -> service.getPostDetail(10L, userPK),
                () -> service.getPostComments(10L, null, 20, userPK))) {
            assertThatThrownBy(request).isInstanceOfSatisfying(BusinessException.class,
                    error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.POST_NOT_FOUND));
        }
        verify(posts, times(2)).findVisibleByPostId(10L, userPK);
        verifyNoInteractions(comments, users, likes, s3);
    }

    @Test
    void detailUsesVisibleCommentCountAndRechecksVisibilityOnEachRequest() {
        when(posts.findVisibleByPostId(10L, userPK)).thenReturn(Optional.empty(), Optional.of(post));
        when(users.findById(userPK)).thenReturn(Optional.of(User.builder().nickname("viewer").build()));
        when(comments.countVisibleByPost(post, userPK)).thenReturn(2L);

        assertThatThrownBy(() -> service.getPostDetail(10L, userPK)).isInstanceOf(BusinessException.class);
        var response = service.getPostDetail(10L, userPK);

        assertThat(response.commentCount()).isEqualTo(2L);
        assertThat(response.author()).isEqualTo("탈퇴한 사용자");
        verify(posts, times(2)).findVisibleByPostId(10L, userPK);
    }

    @Test
    void firstCommentPageBuildsCursorFromLastVisibleComment() {
        readablePost();
        when(comments.findFirstCommentPage(post, userPK, PageRequest.of(0, 3)))
                .thenReturn(List.of(comment(8L), comment(5L), comment(2L)));
        when(comments.countVisibleByPost(post, userPK)).thenReturn(3L);

        var response = service.getPostComments(10L, null, 2, userPK);

        assertThat(response.content()).hasSize(2);
        assertThat(response.totalElements()).isEqualTo(3L);
        assertThat(response.hasNext()).isTrue();
        assertThat(cursors.decode(response.nextCursor(), CommentCursor.class)).isEqualTo(new CommentCursor(time, 5L));
        verify(comments, times(1)).findFirstCommentPage(post, userPK, PageRequest.of(0, 3));
        verifyNoInteractions(s3);
    }

    @Test
    void nextCommentPageKeepsViewerFilterAndDoesNotRecount() {
        readablePost();
        String cursor = cursors.encode(new CommentCursor(time, 5L));
        when(comments.findCommentPageAfterCursor(post, userPK, time, 5L, PageRequest.of(0, 3)))
                .thenReturn(List.of(comment(2L)));

        var response = service.getPostComments(10L, cursor, 2, userPK);

        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isNull();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
        verify(comments, never()).countVisibleByPost(any(), anyString());
    }

    @Test
    void noVisibleCommentsReturnsEmptyPage() {
        readablePost();
        when(comments.findFirstCommentPage(post, userPK, PageRequest.of(0, 21))).thenReturn(List.of());

        var response = service.getPostComments(10L, null, 20, userPK);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    private void readablePost() {
        when(posts.findVisibleByPostId(10L, userPK)).thenReturn(Optional.of(post));
        when(users.existsById(userPK)).thenReturn(true);
    }

    private PostComment comment(Long commentId) {
        return PostComment.builder().postCommentId(commentId).post(post).content("retained")
                .createdAt(time).build();
    }
}
