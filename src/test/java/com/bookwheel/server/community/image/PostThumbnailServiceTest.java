package com.bookwheel.server.community.image;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.community.entity.PostImage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PostThumbnailServiceTest {

    private static final String ORIGINAL_KEY = "posts/9791161571188/abc_image.png";
    private static final String THUMBNAIL_KEY = "posts/9791161571188/abc_image_thumb.jpg";

    @Mock private S3Service s3Service;
    @Mock private PostThumbnailStore postThumbnailStore;

    @InjectMocks private PostThumbnailService postThumbnailService;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("축소본을 업로드하고 썸네일 objectKey를 반환한다")
    void createThumbnail_UploadsAndReturnsKey() throws IOException {
        given(s3Service.getObjectBytes(ORIGINAL_KEY, PostThumbnailGenerator.MAX_SOURCE_BYTES)).willReturn(createImage(1600, 1200));

        String result = postThumbnailService.createThumbnail(ORIGINAL_KEY);

        assertThat(result).isEqualTo(THUMBNAIL_KEY);

        ArgumentCaptor<byte[]> uploaded = ArgumentCaptor.forClass(byte[].class);
        then(s3Service).should().putObject(
                eq(THUMBNAIL_KEY),
                uploaded.capture(),
                eq(PostThumbnailGenerator.CONTENT_TYPE));

        BufferedImage thumbnail = ImageIO.read(new ByteArrayInputStream(uploaded.getValue()));
        assertThat(thumbnail.getWidth()).isEqualTo(400);
        assertThat(thumbnail.getHeight()).isEqualTo(300);
    }

    @Test
    @DisplayName("원본을 읽지 못하면 업로드하지 않고 null을 반환한다")
    void createThumbnail_ReturnsNullWhenOriginalMissing() {
        given(s3Service.getObjectBytes(ORIGINAL_KEY, PostThumbnailGenerator.MAX_SOURCE_BYTES))
                .willThrow(new BusinessException(ErrorCode.FILE_NOT_FOUND));

        String result = postThumbnailService.createThumbnail(ORIGINAL_KEY);

        assertThat(result).isNull();
        then(s3Service).should(never()).putObject(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("이미지로 열 수 없는 원본이면 null을 반환한다")
    void createThumbnail_ReturnsNullWhenOriginalIsNotImage() {
        given(s3Service.getObjectBytes(ORIGINAL_KEY, PostThumbnailGenerator.MAX_SOURCE_BYTES)).willReturn("not an image".getBytes());

        String result = postThumbnailService.createThumbnail(ORIGINAL_KEY);

        assertThat(result).isNull();
        then(s3Service).should(never()).putObject(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("업로드가 실패해도 예외를 밖으로 던지지 않고 null을 반환한다")
    void createThumbnail_ReturnsNullWhenUploadFails() {
        given(s3Service.getObjectBytes(ORIGINAL_KEY, PostThumbnailGenerator.MAX_SOURCE_BYTES)).willReturn(createImage(800, 600));
        willThrow(new BusinessException(ErrorCode.FILE_UPLOAD_ERROR))
                .given(s3Service).putObject(anyString(), any(), anyString());

        String result = postThumbnailService.createThumbnail(ORIGINAL_KEY);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("커밋 전에는 썸네일을 만들지 않고, 커밋 이후에 만들어 반영한다")
    void registerPostCommitThumbnailGeneration_RunsAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        given(s3Service.getObjectBytes(ORIGINAL_KEY, PostThumbnailGenerator.MAX_SOURCE_BYTES)).willReturn(createImage(800, 600));

        postThumbnailService.registerPostCommitThumbnailGeneration(
                List.of(postImage(1L, ORIGINAL_KEY)));

        // 아직 커밋 전이다.
        then(s3Service).should(never()).getObjectBytes(anyString(), anyLong());
        then(postThumbnailStore).should(never()).applyThumbnailKeys(any());

        triggerAfterCommit();

        then(s3Service).should().putObject(eq(THUMBNAIL_KEY), any(), anyString());
        then(postThumbnailStore).should().applyThumbnailKeys(Map.of(1L, THUMBNAIL_KEY));
    }

    @Test
    @DisplayName("썸네일 생성이 실패한 이미지는 반영 대상에서 빠진다")
    void registerPostCommitThumbnailGeneration_SkipsFailedImage() {
        TransactionSynchronizationManager.initSynchronization();
        String brokenKey = "posts/9791161571188/broken_image.png";
        given(s3Service.getObjectBytes(brokenKey, PostThumbnailGenerator.MAX_SOURCE_BYTES)).willReturn("not an image".getBytes());
        given(s3Service.getObjectBytes(ORIGINAL_KEY, PostThumbnailGenerator.MAX_SOURCE_BYTES)).willReturn(createImage(800, 600));

        postThumbnailService.registerPostCommitThumbnailGeneration(
                List.of(postImage(1L, brokenKey), postImage(2L, ORIGINAL_KEY)));
        triggerAfterCommit();

        then(postThumbnailStore).should().applyThumbnailKeys(Map.of(2L, THUMBNAIL_KEY));
    }

    @Test
    @DisplayName("커밋 이후 반영이 실패해도 예외를 밖으로 던지지 않는다")
    void registerPostCommitThumbnailGeneration_SwallowsStoreFailure() {
        TransactionSynchronizationManager.initSynchronization();
        given(s3Service.getObjectBytes(ORIGINAL_KEY, PostThumbnailGenerator.MAX_SOURCE_BYTES)).willReturn(createImage(800, 600));
        willThrow(new IllegalStateException("DB 연결 끊김"))
                .given(postThumbnailStore).applyThumbnailKeys(any());

        postThumbnailService.registerPostCommitThumbnailGeneration(
                List.of(postImage(1L, ORIGINAL_KEY)));

        // 게시물은 이미 커밋됐다. 여기서 예외가 올라가면 성공한 작성이 500 으로 뒤집힌다.
        triggerAfterCommit();
    }

    @Test
    @DisplayName("트랜잭션 동기화가 없으면 등록을 거부한다")
    void registerPostCommitThumbnailGeneration_FailsWithoutActiveTransaction() {
        List<PostImage> images = List.of(postImage(1L, ORIGINAL_KEY));

        assertThatThrownBy(() -> postThumbnailService.registerPostCommitThumbnailGeneration(images))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("이미지가 없으면 아무 것도 등록하지 않는다")
    void registerPostCommitThumbnailGeneration_DoesNothingWithoutImages() {
        postThumbnailService.registerPostCommitThumbnailGeneration(List.of());

        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }

    private void triggerAfterCommit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
    }

    private PostImage postImage(Long postImageId, String objectKey) {
        return PostImage.builder()
                .postImageId(postImageId)
                .objectKey(objectKey)
                .build();
    }

    private byte[] createImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        Random random = new Random(42);
        for (int y = 0; y < height; y += 8) {
            for (int x = 0; x < width; x += 8) {
                graphics.setColor(new Color(random.nextInt(0xFFFFFF)));
                graphics.fillRect(x, y, 8, 8);
            }
        }
        graphics.dispose();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", buffer);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        return buffer.toByteArray();
    }
}
