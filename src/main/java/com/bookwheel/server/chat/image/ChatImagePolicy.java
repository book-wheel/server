package com.bookwheel.server.chat.image;

import com.bookwheel.server.common.dto.S3ObjectMetadata;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ChatImagePolicy {

    public static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    public static final int SIGNATURE_LENGTH = 12;

    private static final String TEMP_PREFIX = "chat-temp/";
    private static final String FINAL_PREFIX = "chat/";

    private static final Map<String, String> CONTENT_TYPE_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "heic", "image/heic",
            "heif", "image/heif"
    );

    private ChatImagePolicy() {
    }

    public static ValidatedImage validateUploadRequest(String fileName, String contentType, Long fileSize) {
        validateFileSize(fileSize);
        String extension = extractExtension(fileName);
        String normalizedContentType = normalizeContentType(contentType);
        validateContentTypeMatchesExtension(extension, normalizedContentType);
        return new ValidatedImage(extension, normalizedContentType);
    }

    public static void validateOwnedTemporaryObjectKey(String imageKey, String chatRoomId, String userPK) {
        if (!StringUtils.hasText(imageKey)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_KEY);
        }

        String expectedPrefix = TEMP_PREFIX + chatRoomId + "/" + userPK + "/";
        if (!imageKey.equals(imageKey.trim()) || !imageKey.startsWith(expectedPrefix)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_KEY);
        }

        String fileName = imageKey.substring(expectedPrefix.length());
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new BusinessException(ErrorCode.INVALID_FILE_KEY);
        }

        String extension = extractExtension(fileName);
        String uuidPart = fileName.substring(0, fileName.length() - extension.length() - 1);
        try {
            UUID.fromString(uuidPart);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_FILE_KEY);
        }
    }

    public static String toFinalObjectKey(String temporaryObjectKey) {
        if (!StringUtils.hasText(temporaryObjectKey) || !temporaryObjectKey.startsWith(TEMP_PREFIX)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_KEY);
        }
        return FINAL_PREFIX + temporaryObjectKey.substring(TEMP_PREFIX.length());
    }

    public static void validateUploadedObject(
            String imageKey,
            S3ObjectMetadata metadata,
            byte[] signature
    ) {
        if (metadata == null || metadata.contentLength() <= 0 || !StringUtils.hasText(metadata.eTag())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        validateFileSize(metadata.contentLength());

        String extension = extractExtension(imageKey.substring(imageKey.lastIndexOf('/') + 1));
        String normalizedContentType = normalizeContentType(metadata.contentType());
        validateContentTypeMatchesExtension(extension, normalizedContentType);
        validateImageSignature(extension, signature);
    }

    private static void validateFileSize(Long fileSize) {
        if (fileSize == null || fileSize <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (fileSize > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED);
        }
    }

    private static String extractExtension(String fileName) {
        if (!StringUtils.hasText(fileName)
                || fileName.contains("/")
                || fileName.contains("\\")) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }

        String trimmedFileName = fileName.trim();
        int dotIndex = trimmedFileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == trimmedFileName.length() - 1) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }

        String extension = trimmedFileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        if (!CONTENT_TYPE_BY_EXTENSION.containsKey(extension)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }
        return extension;
    }

    private static String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }
        String trimmedContentType = contentType.trim();
        if (!contentType.equals(trimmedContentType)
                || !contentType.equals(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }
        return contentType;
    }

    private static void validateContentTypeMatchesExtension(String extension, String contentType) {
        if (!CONTENT_TYPE_BY_EXTENSION.get(extension).equals(contentType)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }
    }

    private static void validateImageSignature(String extension, byte[] signature) {
        boolean validSignature = switch (extension) {
            case "jpg", "jpeg" -> hasBytes(signature, 0, 0xFF, 0xD8, 0xFF);
            case "png" -> hasBytes(signature, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "webp" -> hasBytes(signature, 0, 'R', 'I', 'F', 'F')
                    && hasBytes(signature, 8, 'W', 'E', 'B', 'P');
            case "heic" -> hasFileTypeBrand(signature, "heic", "heix", "hevc", "hevx");
            case "heif" -> hasFileTypeBrand(signature, "mif1", "msf1");
            default -> false;
        };
        if (!validSignature) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }
    }

    private static boolean hasFileTypeBrand(byte[] signature, String... brands) {
        if (!hasBytes(signature, 4, 'f', 't', 'y', 'p')) {
            return false;
        }
        for (String brand : brands) {
            if (hasBytes(signature, 8, brand.charAt(0), brand.charAt(1), brand.charAt(2), brand.charAt(3))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasBytes(byte[] actual, int offset, int... expected) {
        if (actual == null || actual.length < offset + expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (Byte.toUnsignedInt(actual[offset + index]) != expected[index]) {
                return false;
            }
        }
        return true;
    }

    public record ValidatedImage(String extension, String contentType) {
    }
}
