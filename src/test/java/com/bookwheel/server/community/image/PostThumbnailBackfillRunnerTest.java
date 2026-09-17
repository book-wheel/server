package com.bookwheel.server.community.image;

import com.bookwheel.server.admin.repository.PostImageRepository;
import com.bookwheel.server.community.entity.PostImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PostThumbnailBackfillRunnerTest {

    @Mock private PostImageRepository postImageRepository;
    @Mock private PostThumbnailService postThumbnailService;

    @InjectMocks private PostThumbnailBackfillRunner runner;

    @Test
    @DisplayName("썸네일이 없는 이미지를 채우고 저장한다")
    void run_FillsMissingThumbnailKeys() {
        PostImage first = postImage(1L, "posts/1/a_image.png");
        PostImage second = postImage(2L, "posts/1/b_image.png");
        givenBatches(List.of(first, second));
        given(postThumbnailService.createThumbnail("posts/1/a_image.png"))
                .willReturn("posts/1/a_image_thumb.jpg");
        given(postThumbnailService.createThumbnail("posts/1/b_image.png"))
                .willReturn("posts/1/b_image_thumb.jpg");

        runner.run(null);

        assertThat(first.getThumbnailKey()).isEqualTo("posts/1/a_image_thumb.jpg");
        assertThat(second.getThumbnailKey()).isEqualTo("posts/1/b_image_thumb.jpg");
        then(postImageRepository).should().saveAll(List.of(first, second));
    }

    @Test
    @DisplayName("생성에 실패한 이미지는 건너뛰고 나머지를 계속 처리한다")
    void run_SkipsFailedImageAndContinues() {
        PostImage failing = postImage(1L, "posts/1/broken_image.heic");
        PostImage succeeding = postImage(2L, "posts/1/b_image.png");
        givenBatches(List.of(failing, succeeding));
        given(postThumbnailService.createThumbnail("posts/1/broken_image.heic")).willReturn(null);
        given(postThumbnailService.createThumbnail("posts/1/b_image.png"))
                .willReturn("posts/1/b_image_thumb.jpg");

        runner.run(null);

        assertThat(failing.getThumbnailKey()).isNull();
        assertThat(succeeding.getThumbnailKey()).isEqualTo("posts/1/b_image_thumb.jpg");
    }

    @Test
    @DisplayName("한 묶음을 다 처리하면 마지막 id 이후부터 다음 묶음을 가져온다")
    void run_AdvancesCursorPastProcessedRows() {
        PostImage first = postImage(7L, "posts/1/a_image.png");
        PostImage second = postImage(9L, "posts/1/b_image.png");
        given(postImageRepository.findRepresentativesMissingThumbnailAfter(eq(0L), any(Pageable.class)))
                .willReturn(List.of(first));
        given(postImageRepository.findRepresentativesMissingThumbnailAfter(eq(7L), any(Pageable.class)))
                .willReturn(List.of(second));
        given(postImageRepository.findRepresentativesMissingThumbnailAfter(eq(9L), any(Pageable.class)))
                .willReturn(List.of());
        given(postThumbnailService.createThumbnail(anyString())).willReturn("posts/1/any_thumb.jpg");

        runner.run(null);

        then(postImageRepository).should()
                .findRepresentativesMissingThumbnailAfter(eq(9L), any(Pageable.class));
    }

    @Test
    @DisplayName("대상이 없으면 아무 것도 저장하지 않는다")
    void run_DoesNothingWhenNoTargets() {
        given(postImageRepository.findRepresentativesMissingThumbnailAfter(eq(0L), any(Pageable.class)))
                .willReturn(List.of());

        runner.run(null);

        then(postImageRepository).should(never()).saveAll(any());
        then(postThumbnailService).should(never()).createThumbnail(anyString());
    }

    // 첫 조회에서 batch 를 돌려주고, 그 다음 조회부터는 비운다.
    private void givenBatches(List<PostImage> batch) {
        long lastPostImageId = batch.get(batch.size() - 1).getPostImageId();
        given(postImageRepository.findRepresentativesMissingThumbnailAfter(eq(0L), any(Pageable.class)))
                .willReturn(batch);
        given(postImageRepository.findRepresentativesMissingThumbnailAfter(
                eq(lastPostImageId), any(Pageable.class)))
                .willReturn(List.of());
    }

    private PostImage postImage(Long postImageId, String objectKey) {
        return PostImage.builder()
                .postImageId(postImageId)
                .objectKey(objectKey)
                .build();
    }
}
