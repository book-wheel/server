package com.bookwheel.server.community.image;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.filters.ImageFilter;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

// 갤러리 대표 이미지로 쓸 축소본을 만든다.
// 원본은 1.8MB 수준이라 18장짜리 갤러리 한 화면이 33MB에 달했다.
public final class PostThumbnailGenerator {

    public static final int MAX_EDGE_PIXELS = 400;
    public static final String EXTENSION = "jpg";
    public static final String CONTENT_TYPE = "image/jpeg";

    // 원본을 통째로 힙에 올려 디코딩하므로 상한이 필요하다.
    // 게시물 이미지는 presigned PUT 에 contentLength 조건이 없어 서버가 업로드 크기를 강제하지 못한다.
    // 게다가 픽셀 수는 파일 크기와 비례하지 않는다. 20000x20000 PNG 는 몇 MB 로도 압축되지만
    // 디코딩하면 width * height * 4 바이트(약 1.6GB)의 래스터가 되어 JVM 을 죽인다.
    public static final long MAX_SOURCE_BYTES = 20L * 1024 * 1024;
    public static final long MAX_SOURCE_PIXELS = 50_000_000L;

    private static final String THUMBNAIL_SUFFIX = "_thumb";
    private static final double OUTPUT_QUALITY = 0.8;

    // JPEG 에는 알파 채널이 없다. 투명한 픽셀을 그대로 RGB 로 옮기면 검은색이 되므로 흰 배경에 합성한다.
    private static final ImageFilter FLATTEN_ON_WHITE = source -> {
        BufferedImage flattened =
                new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = flattened.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return flattened;
    };

    private PostThumbnailGenerator() {
    }

    // 긴 변을 400px로 맞춘 JPEG 바이트를 반환한다. 원본이 이미 400px 이하면 확대하지 않는다.
    public static byte[] generate(byte[] originalImage) {
        if (originalImage == null || originalImage.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }

        if (originalImage.length > MAX_SOURCE_BYTES) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
        }

        // 픽셀을 디코딩하기 전에 헤더만 읽어 크기를 확인한다. 상한을 넘으면 힙을 잡기 전에 막는다.
        Dimension sourceSize = readImageSize(originalImage);
        if ((long) sourceSize.width * sourceSize.height > MAX_SOURCE_PIXELS) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
        }

        // 긴 변 기준 정사각 박스에 맞춘다. 박스가 원본보다 크지 않으므로 확대되지 않고,
        // EXIF 회전으로 가로세로가 뒤바뀌어도 같은 값이라 결과가 달라지지 않는다.
        int targetEdge = Math.min(Math.max(sourceSize.width, sourceSize.height), MAX_EDGE_PIXELS);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            // 원본 바이트 스트림을 그대로 넘겨야 Thumbnailator 가 EXIF Orientation 을 적용한다.
            // 미리 ImageIO.read 로 디코딩한 BufferedImage 를 넘기면 회전 정보가 이미 사라진 뒤라
            // 세워서 찍은 휴대폰 사진의 썸네일만 눕는다.
            Thumbnails.of(new ByteArrayInputStream(originalImage))
                    .size(targetEdge, targetEdge)
                    .imageType(BufferedImage.TYPE_INT_ARGB)
                    .addFilter(FLATTEN_ON_WHITE)
                    .outputFormat(EXTENSION)
                    .outputQuality(OUTPUT_QUALITY)
                    .toOutputStream(output);
        } catch (IOException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }

        return output.toByteArray();
    }

    // 원본 objectKey에서 썸네일 objectKey를 만든다. posts/1/uuid_image.png -> posts/1/uuid_image_thumb.jpg
    public static String toThumbnailKey(String originalKey) {
        if (originalKey == null || originalKey.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_FILE_KEY);
        }

        int extensionIndex = originalKey.lastIndexOf('.');
        int separatorIndex = originalKey.lastIndexOf('/');
        String withoutExtension = extensionIndex > separatorIndex
                ? originalKey.substring(0, extensionIndex)
                : originalKey;

        return withoutExtension + THUMBNAIL_SUFFIX + "." + EXTENSION;
    }

    private static Dimension readImageSize(byte[] originalImage) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(originalImage))) {
            if (input == null) {
                throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                // 업로드는 허용하지만 JDK 기본 ImageIO 에 리더가 없는 포맷(HEIC/HEIF/WebP)이 여기로 온다.
                throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                return new Dimension(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }
    }
}
