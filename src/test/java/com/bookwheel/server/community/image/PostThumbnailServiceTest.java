package com.bookwheel.server.community.image;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.service.S3Service;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks private PostThumbnailService postThumbnailService;

    @Test
    @DisplayName("축소본을 업로드하고 썸네일 objectKey를 반환한다")
    void createThumbnail_UploadsAndReturnsKey() throws IOException {
        given(s3Service.getObjectBytes(ORIGINAL_KEY)).willReturn(createImage(1600, 1200));

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
        given(s3Service.getObjectBytes(ORIGINAL_KEY))
                .willThrow(new BusinessException(ErrorCode.FILE_NOT_FOUND));

        String result = postThumbnailService.createThumbnail(ORIGINAL_KEY);

        assertThat(result).isNull();
        then(s3Service).should(never()).putObject(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("이미지로 열 수 없는 원본이면 null을 반환한다")
    void createThumbnail_ReturnsNullWhenOriginalIsNotImage() {
        given(s3Service.getObjectBytes(ORIGINAL_KEY)).willReturn("not an image".getBytes());

        String result = postThumbnailService.createThumbnail(ORIGINAL_KEY);

        assertThat(result).isNull();
        then(s3Service).should(never()).putObject(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("업로드가 실패해도 예외를 밖으로 던지지 않고 null을 반환한다")
    void createThumbnail_ReturnsNullWhenUploadFails() {
        given(s3Service.getObjectBytes(ORIGINAL_KEY)).willReturn(createImage(800, 600));
        willThrow(new BusinessException(ErrorCode.FILE_UPLOAD_ERROR))
                .given(s3Service).putObject(anyString(), any(), anyString());

        String result = postThumbnailService.createThumbnail(ORIGINAL_KEY);

        assertThat(result).isNull();
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
