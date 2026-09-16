package com.bookwheel.server.community.image;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import net.coobird.thumbnailator.Thumbnails;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

// 갤러리 대표 이미지로 쓸 축소본을 만든다.
// 원본은 1.8MB 수준이라 18장짜리 갤러리 한 화면이 33MB에 달했다.
public final class PostThumbnailGenerator {

    public static final int MAX_EDGE_PIXELS = 400;
    public static final String EXTENSION = "jpg";
    public static final String CONTENT_TYPE = "image/jpeg";

    private static final String THUMBNAIL_SUFFIX = "_thumb";
    private static final double OUTPUT_QUALITY = 0.8;

    private PostThumbnailGenerator() {
    }

    // 긴 변을 400px로 맞춘 JPEG 바이트를 반환한다. 원본이 이미 400px 이하면 확대하지 않는다.
    public static byte[] generate(byte[] originalImage) {
        if (originalImage == null || originalImage.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }

        BufferedImage source = readImage(originalImage);
        double scale = resolveScale(source);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            Thumbnails.of(source)
                    .scale(scale)
                    // JPEG에는 알파 채널이 없다. 지정하지 않으면 투명 PNG 변환이 실패한다.
                    .imageType(BufferedImage.TYPE_INT_RGB)
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

    private static BufferedImage readImage(byte[] originalImage) {
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(originalImage));
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }

        // ImageIO는 디코더를 못 찾으면 예외 대신 null을 돌려준다(예: HEIC).
        if (source == null) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }

        return source;
    }

    private static double resolveScale(BufferedImage source) {
        int longestEdge = Math.max(source.getWidth(), source.getHeight());
        if (longestEdge <= MAX_EDGE_PIXELS) {
            return 1.0;
        }

        return (double) MAX_EDGE_PIXELS / longestEdge;
    }
}
