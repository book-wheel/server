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

import java.util.List;

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

        postDeletionService.delete(post);

        InOrder inOrder = inOrder(notificationService, postCommentRepository, postLikeRepository, postRepository);
        inOrder.verify(notificationService).deleteByPostId(10L);
        inOrder.verify(postCommentRepository).deleteAllByPost(post);
        inOrder.verify(postLikeRepository).deleteAllByPost(post);
        inOrder.verify(postRepository).delete(post);
        then(s3Service).should().deleteObject("posts/10/image.jpg");
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

        postDeletionService.delete(post);

        then(s3Service).should().deleteObject("posts/10/image.jpg");
        then(s3Service).shouldHaveNoMoreInteractions();
    }
}
