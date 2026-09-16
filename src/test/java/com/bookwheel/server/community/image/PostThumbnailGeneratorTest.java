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
import java.util.zip.CRC32;
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
    @DisplayName("투명한 PNG는 검은색이 아니라 흰 배경에 합성한다")
    void generate_FlattensTransparentPngOnWhite() throws IOException {
        BufferedImage source = new BufferedImage(800, 800, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(source, "png", buffer);

        BufferedImage thumbnail = readImage(PostThumbnailGenerator.generate(buffer.toByteArray()));

        assertThat(thumbnail.getWidth()).isEqualTo(400);
        // JPEG 는 손실 압축이라 정확히 255가 아닐 수 있다. 검은색(0)과 구분되면 충분하다.
        Color center = new Color(thumbnail.getRGB(200, 200));
        assertThat(center.getRed()).isGreaterThan(240);
        assertThat(center.getGreen()).isGreaterThan(240);
        assertThat(center.getBlue()).isGreaterThan(240);
    }

    @Test
    @DisplayName("EXIF Orientation이 세로로 걸린 사진은 회전한 결과를 기준으로 축소한다")
    void generate_AppliesExifOrientation() throws IOException {
        // 가로 1200 x 세로 600 으로 저장돼 있지만 Orientation=6 이라 보는 사람에게는 600x1200 이다.
        byte[] original = createJpegWithExifOrientation(1200, 600, 6);

        BufferedImage thumbnail = readImage(PostThumbnailGenerator.generate(original));

        // 회전을 적용하지 않으면 400x200 이 된다.
        assertThat(thumbnail.getWidth()).isEqualTo(200);
        assertThat(thumbnail.getHeight()).isEqualTo(400);
    }

    @Test
    @DisplayName("원본 바이트가 상한을 넘으면 디코딩하지 않고 FILE_SIZE_EXCEEDED로 거절한다")
    void generate_RejectsOversizedBytes() {
        byte[] tooLarge = new byte[(int) PostThumbnailGenerator.MAX_SOURCE_BYTES + 1];

        assertThatThrownBy(() -> PostThumbnailGenerator.generate(tooLarge))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED);
    }

    @Test
    @DisplayName("픽셀 수가 상한을 넘는 이미지는 래스터를 할당하기 전에 거절한다")
    void generate_RejectsTooManyPixels() {
        // 헤더만 20000x20000(4억 픽셀)로 선언한 PNG. 실제로 디코딩하면 1.6GB 래스터가 된다.
        byte[] declaredHuge = createPngHeaderOnly(20000, 20000);

        assertThatThrownBy(() -> PostThumbnailGenerator.generate(declaredHuge))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED);
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

    // Orientation 태그 하나만 담은 최소 EXIF(APP1)를 JPEG 앞에 끼워 넣는다.
    private byte[] createJpegWithExifOrientation(int width, int height, int orientation) throws IOException {
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(toOpaqueImage(width, height), "jpg", jpeg);
        byte[] jpegBytes = jpeg.toByteArray();

        ByteArrayOutputStream exif = new ByteArrayOutputStream();
        exif.write(new byte[]{'E', 'x', 'i', 'f', 0, 0});
        exif.write(new byte[]{'M', 'M', 0, 42, 0, 0, 0, 8});     // 빅엔디안 TIFF 헤더, IFD0 오프셋 8
        exif.write(new byte[]{0, 1});                             // IFD 항목 1개
        exif.write(new byte[]{1, 0x12});                          // 태그 0x0112 Orientation
        exif.write(new byte[]{0, 3});                             // 타입 SHORT
        exif.write(new byte[]{0, 0, 0, 1});                       // 개수 1
        exif.write(new byte[]{0, (byte) orientation, 0, 0});      // 값(SHORT 는 앞 2바이트)
        exif.write(new byte[]{0, 0, 0, 0});                       // 다음 IFD 없음
        byte[] exifPayload = exif.toByteArray();

        int segmentLength = exifPayload.length + 2;
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write(new byte[]{(byte) 0xFF, (byte) 0xD8});       // SOI
        result.write(new byte[]{(byte) 0xFF, (byte) 0xE1});       // APP1
        result.write(new byte[]{(byte) (segmentLength >> 8), (byte) segmentLength});
        result.write(exifPayload);
        result.write(jpegBytes, 2, jpegBytes.length - 2);         // 원본 JPEG 의 SOI 는 뺀다
        return result.toByteArray();
    }

    // IHDR 까지만 담은 PNG. 크기 선언은 유효하므로 헤더 조회는 성공하지만 픽셀 데이터는 없다.
    private byte[] createPngHeaderOnly(int width, int height) {
        byte[] header = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] ihdr = {
                'I', 'H', 'D', 'R',
                (byte) (width >> 24), (byte) (width >> 16), (byte) (width >> 8), (byte) width,
                (byte) (height >> 24), (byte) (height >> 16), (byte) (height >> 8), (byte) height,
                8, 2, 0, 0, 0
        };
        CRC32 crc = new CRC32();
        crc.update(ihdr);
        long checksum = crc.getValue();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        buffer.writeBytes(header);
        buffer.writeBytes(new byte[]{0, 0, 0, 13});               // IHDR 길이
        buffer.writeBytes(ihdr);
        buffer.writeBytes(new byte[]{
                (byte) (checksum >> 24), (byte) (checksum >> 16), (byte) (checksum >> 8), (byte) checksum
        });
        return buffer.toByteArray();
    }

    private BufferedImage toOpaqueImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.GRAY);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return image;
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
