package com.bookwheel.server.community.service;

import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.entity.PostImage;
import com.bookwheel.server.community.repository.PostCommentRepository;
import com.bookwheel.server.community.repository.PostLikeRepository;
import com.bookwheel.server.community.repository.PostRepository;
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
    @Mock private S3Service s3Service;

    @InjectMocks
    private PostDeletionService postDeletionService;

    @Test
    @DisplayName("게시물 삭제는 댓글과 좋아요를 먼저 삭제한 뒤 게시물을 삭제하고 이미지 객체를 정리한다")
    void delete_RemovesRelatedRowsBeforePostAndDeletesImages() {
        Post post = mock(Post.class);
        PostImage image = mock(PostImage.class);

        given(post.getImages()).willReturn(List.of(image));
        given(image.getObjectKey()).willReturn("posts/10/image.jpg");

        postDeletionService.delete(post);

        InOrder inOrder = inOrder(postCommentRepository, postLikeRepository, postRepository);
        inOrder.verify(postCommentRepository).deleteByPost(post);
        inOrder.verify(postLikeRepository).deleteByPost(post);
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
