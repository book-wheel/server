package com.bookwheel.server.community.service;

import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.entity.PostImage;
import com.bookwheel.server.community.repository.PostCommentRepository;
import com.bookwheel.server.community.repository.PostLikeRepository;
import com.bookwheel.server.community.repository.PostRepository;
import com.bookwheel.server.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class PostDeletionServiceTest {

    @Mock private PostCommentRepository postCommentRepository;
    @Mock private PostLikeRepository postLikeRepository;
    @Mock private PostRepository postRepository;
    @Mock private NotificationService notificationService;
    @Mock private S3Service s3Service;

    @InjectMocks
    private PostDeletionService postDeletionService;

    @Test
    @DisplayName("게시물 삭제는 알림·댓글·좋아요를 먼저 삭제한 뒤 게시물을 삭제하고 이미지 객체를 정리한다")
    void delete_RemovesRelatedRowsBeforePostAndDeletesImages() {
        Post post = mock(Post.class);
        PostImage image = mock(PostImage.class);

        given(post.getPostId()).willReturn(10L);
        given(post.getImages()).willReturn(List.of(image));
        given(image.getObjectKey()).willReturn("posts/10/image.jpg");

        TransactionSynchronizationManager.initSynchronization();
        try {
            postDeletionService.delete(post);

            InOrder inOrder = inOrder(notificationService, postCommentRepository, postLikeRepository, postRepository);
            // 알림을 정리하기 전에 게시물 행을 잠가야, 정리 이후에 비동기 알림 저장이 끼어들지 못한다.
            inOrder.verify(postRepository).findByPostIdForUpdate(10L);
            inOrder.verify(notificationService).deleteByPostId(10L);
            inOrder.verify(postCommentRepository).deleteAllByPost(post);
            inOrder.verify(postLikeRepository).deleteAllByPost(post);
            inOrder.verify(postRepository).delete(post);

            // 커밋이 끝나기 전에는 S3 객체를 건드리지 않는다.
            then(s3Service).shouldHaveNoInteractions();
            TransactionSynchronizationManager.getSynchronizations().forEach(synchronization -> synchronization.afterCommit());
            then(s3Service).should().deleteObject("posts/10/image.jpg");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("게시물 삭제는 중복되거나 빈 이미지 키를 S3 삭제 대상에서 제외한다")
    void delete_FiltersImageObjectKeys() {
        Post post = mock(Post.class);
        PostImage image = mock(PostImage.class);
        PostImage duplicateImage = mock(PostImage.class);
        PostImage blankImage = mock(PostImage.class);

        given(post.getImages()).willReturn(List.of(image, duplicateImage, blankImage));
        given(image.getObjectKey()).willReturn("posts/10/image.jpg");
        given(duplicateImage.getObjectKey()).willReturn("posts/10/image.jpg");
        given(blankImage.getObjectKey()).willReturn(" ");

        TransactionSynchronizationManager.initSynchronization();
        try {
            postDeletionService.delete(post);

            TransactionSynchronizationManager.getSynchronizations().forEach(synchronization -> synchronization.afterCommit());
            then(s3Service).should().deleteObject("posts/10/image.jpg");
            then(s3Service).shouldHaveNoMoreInteractions();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("게시물 삭제는 트랜잭션 동기화가 없으면 이미지를 지우지 않고 실패한다")
    void delete_FailsWhenTransactionSynchronizationIsNotActive() {
        Post post = mock(Post.class);
        PostImage image = mock(PostImage.class);

        given(post.getImages()).willReturn(List.of(image));
        given(image.getObjectKey()).willReturn("posts/10/image.jpg");

        // 커밋 여부를 알 수 없는 상태에서 S3를 지우면 롤백된 게시물의 이미지까지 잃는다.
        assertThatThrownBy(() -> postDeletionService.delete(post))
                .isInstanceOf(IllegalStateException.class);

        then(s3Service).shouldHaveNoInteractions();
    }
}
