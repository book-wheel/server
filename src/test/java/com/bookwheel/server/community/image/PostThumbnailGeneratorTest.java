package com.bookwheel.server.community.image;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostThumbnailGeneratorTest {

    @Test
    @DisplayName("긴 변이 400px를 넘으면 400px로 줄이고 가로세로 비율을 유지한다")
    void generate_ResizesLongestEdgeTo400() throws IOException {
        byte[] original = createImage(2000, 1000, "png");

        BufferedImage thumbnail = readImage(PostThumbnailGenerator.generate(original));

        assertThat(thumbnail.getWidth()).isEqualTo(400);
        assertThat(thumbnail.getHeight()).isEqualTo(200);
    }

    @Test
    @DisplayName("세로가 더 긴 이미지도 세로를 400px로 맞춘다")
    void generate_ResizesPortraitByHeight() throws IOException {
        byte[] original = createImage(600, 1200, "png");

        BufferedImage thumbnail = readImage(PostThumbnailGenerator.generate(original));

        assertThat(thumbnail.getWidth()).isEqualTo(200);
        assertThat(thumbnail.getHeight()).isEqualTo(400);
    }

    @Test
    @DisplayName("이미 400px 이하인 이미지는 확대하지 않는다")
    void generate_DoesNotUpscaleSmallImage() throws IOException {
        byte[] original = createImage(150, 100, "png");

        BufferedImage thumbnail = readImage(PostThumbnailGenerator.generate(original));

        assertThat(thumbnail.getWidth()).isEqualTo(150);
        assertThat(thumbnail.getHeight()).isEqualTo(100);
    }

    @Test
    @DisplayName("결과물은 JPEG이며 원본보다 훨씬 작다")
    void generate_ProducesMuchSmallerJpeg() {
        byte[] original = createImage(2400, 1600, "png");

        byte[] thumbnail = PostThumbnailGenerator.generate(original);

        assertThat(readFormatName(thumbnail)).isEqualTo("JPEG");
        assertThat(thumbnail.length).isLessThan(original.length / 10);
    }

    @Test
    @DisplayName("투명 영역이 있는 PNG도 JPEG로 변환한다")
    void generate_ConvertsTransparentPng() throws IOException {
        BufferedImage source = new BufferedImage(800, 800, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(source, "png", buffer);

        BufferedImage thumbnail = readImage(PostThumbnailGenerator.generate(buffer.toByteArray()));

        assertThat(thumbnail.getWidth()).isEqualTo(400);
    }

    @Test
    @DisplayName("이미지가 아닌 바이트는 INVALID_FILE_FORMAT으로 거절한다")
    void generate_RejectsNonImageBytes() {
        byte[] notAnImage = "this is not an image".getBytes();

        assertThatThrownBy(() -> PostThumbnailGenerator.generate(notAnImage))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE_FORMAT);
    }

    @Test
    @DisplayName("빈 바이트 배열은 INVALID_FILE_FORMAT으로 거절한다")
    void generate_RejectsEmptyBytes() {
        assertThatThrownBy(() -> PostThumbnailGenerator.generate(new byte[0]))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE_FORMAT);
    }

    @Test
    @DisplayName("null은 INVALID_FILE_FORMAT으로 거절한다")
    void generate_RejectsNull() {
        assertThatThrownBy(() -> PostThumbnailGenerator.generate(null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE_FORMAT);
    }

    @ParameterizedTest
    @CsvSource({
            "posts/9791161571188/abc_image.png,posts/9791161571188/abc_image_thumb.jpg",
            "posts/9791161571188/abc_image.JPEG,posts/9791161571188/abc_image_thumb.jpg",
            "posts/9791161571188/abc_image,posts/9791161571188/abc_image_thumb.jpg",
            "posts/book.v2/abc_image,posts/book.v2/abc_image_thumb.jpg"
    })
    @DisplayName("원본 키의 확장자를 썸네일 확장자로 바꾼다")
    void toThumbnailKey_ReplacesExtension(String originalKey, String expected) {
        assertThat(PostThumbnailGenerator.toThumbnailKey(originalKey)).isEqualTo(expected);
    }

    @Test
    @DisplayName("비어 있는 키는 INVALID_FILE_KEY로 거절한다")
    void toThumbnailKey_RejectsBlankKey() {
        assertThatThrownBy(() -> PostThumbnailGenerator.toThumbnailKey(" "))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE_KEY);
    }

    // 무압축에 가깝게 만들려고 노이즈를 채운다. 단색이면 PNG가 과하게 압축돼 크기 비교가 무의미해진다.
    private byte[] createImage(int width, int height, String format) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        Random random = new Random(42);
        for (int y = 0; y < height; y += 4) {
            for (int x = 0; x < width; x += 4) {
                graphics.setColor(new Color(random.nextInt(0xFFFFFF)));
                graphics.fillRect(x, y, 4, 4);
            }
        }
        graphics.dispose();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, format, buffer);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        return buffer.toByteArray();
    }

    private BufferedImage readImage(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private String readFormatName(byte[] bytes) {
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(stream);
            return readers.hasNext() ? readers.next().getFormatName() : null;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
