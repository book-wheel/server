package com.bookwheel.server.user.image;

import com.bookwheel.server.common.dto.S3ObjectMetadata;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ProfileImagePolicy {

    public static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    public static final int SIGNATURE_PROBE_LENGTH = 64;
    public static final String TEMPORARY_PREFIX = "profiles-temp/";
    public static final String FINAL_PREFIX = "profiles/";

    private static final int STANDARD_BOX_HEADER_LENGTH = 8;
    private static final int LARGE_BOX_HEADER_LENGTH = 16;
    private static final int FILE_TYPE_FIELDS_LENGTH = 8;

    private static final Map<String, String> CONTENT_TYPE_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "heic", "image/heic",
            "heif", "image/heif"
    );

    private ProfileImagePolicy() {
    }

    public static ValidatedImage validateUploadRequest(String fileName, String contentType, Long fileSize) {
        validateFileSize(fileSize);
        String extension = extractExtension(fileName);
        String normalizedContentType = normalizeContentType(contentType);
        validateContentTypeMatchesExtension(extension, normalizedContentType);
        return new ValidatedImage(extension, normalizedContentType);
    }

    public static String createTemporaryObjectKey(String userPK, String extension) {
        if (!StringUtils.hasText(userPK) || !CONTENT_TYPE_BY_EXTENSION.containsKey(extension)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return TEMPORARY_PREFIX + userPK + "/" + UUID.randomUUID() + "." + extension;
    }

    public static void validateOwnedTemporaryObjectKey(String objectKey, String userPK) {
        if (!StringUtils.hasText(objectKey) || !StringUtils.hasText(userPK)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_KEY);
        }

        String expectedPrefix = TEMPORARY_PREFIX + userPK + "/";
        if (!objectKey.equals(objectKey.trim()) || !objectKey.startsWith(expectedPrefix)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_KEY);
        }

        String fileName = objectKey.substring(expectedPrefix.length());
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

    public static String createFinalObjectKey(String temporaryObjectKey, String userPK) {
        validateOwnedTemporaryObjectKey(temporaryObjectKey, userPK);
        String fileName = temporaryObjectKey.substring(temporaryObjectKey.lastIndexOf('/') + 1);
        String extension = extractExtension(fileName);
        // 동일한 임시 key를 재시도해도 시도별 보상 삭제 대상이 겹치지 않게 한다.
        return FINAL_PREFIX + userPK + "/" + UUID.randomUUID() + "." + extension;
    }

    public static boolean isStoredProfileObjectKey(String objectKey) {
        return StringUtils.hasText(objectKey) && objectKey.startsWith(FINAL_PREFIX);
    }

    public static int getSignatureProbeLength(S3ObjectMetadata metadata) {
        validateMetadataBasics(metadata);
        return (int) Math.min(metadata.contentLength(), SIGNATURE_PROBE_LENGTH);
    }

    public static int determineSignatureLength(
            String objectKey,
            S3ObjectMetadata metadata,
            byte[] signatureProbe
    ) {
        String extension = validateUploadedMetadata(objectKey, metadata);
        if (signatureProbe == null || signatureProbe.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }
        if (!extension.equals("heic") && !extension.equals("heif")) {
            return signatureProbe.length;
        }
        return parseFileTypeBoxHeader(signatureProbe, metadata.contentLength()).boxSize();
    }

    public static void validateUploadedObject(
            String objectKey,
            S3ObjectMetadata metadata,
            byte[] signature
    ) {
        String extension = validateUploadedMetadata(objectKey, metadata);
        validateImageSignature(extension, metadata.contentLength(), signature);
    }

    private static String validateUploadedMetadata(String objectKey, S3ObjectMetadata metadata) {
        validateMetadataBasics(metadata);
        String fileName = objectKey.substring(objectKey.lastIndexOf('/') + 1);
        String extension = extractExtension(fileName);
        String normalizedContentType = normalizeContentType(metadata.contentType());
        validateContentTypeMatchesExtension(extension, normalizedContentType);
        return extension;
    }

    private static void validateMetadataBasics(S3ObjectMetadata metadata) {
        if (metadata == null || metadata.contentLength() <= 0 || !StringUtils.hasText(metadata.eTag())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        validateFileSize(metadata.contentLength());
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

    private static void validateImageSignature(String extension, long contentLength, byte[] signature) {
        boolean validSignature = switch (extension) {
            case "jpg", "jpeg" -> hasBytes(signature, 0, 0xFF, 0xD8, 0xFF);
            case "png" -> hasBytes(signature, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "webp" -> hasBytes(signature, 0, 'R', 'I', 'F', 'F')
                    && hasBytes(signature, 8, 'W', 'E', 'B', 'P');
            case "heic" -> hasCompatibleFileTypeBrand(
                    signature,
                    contentLength,
                    "heic", "heix", "heim", "heis"
            );
            case "heif" -> hasCompatibleFileTypeBrand(signature, contentLength, "mif1");
            default -> false;
        };
        if (!validSignature) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }
    }

    private static boolean hasCompatibleFileTypeBrand(
            byte[] signature,
            long contentLength,
            String... supportedBrands
    ) {
        FileTypeBoxHeader header = parseFileTypeBoxHeader(signature, contentLength);
        if (signature.length < header.boxSize()) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }

        int compatibleBrandOffset = header.headerSize() + FILE_TYPE_FIELDS_LENGTH;
        for (int offset = compatibleBrandOffset; offset < header.boxSize(); offset += 4) {
            if (hasSupportedBrand(signature, offset, supportedBrands)) {
                return true;
            }
        }
        return false;
    }

    private static FileTypeBoxHeader parseFileTypeBoxHeader(byte[] bytes, long contentLength) {
        if (bytes == null
                || bytes.length < STANDARD_BOX_HEADER_LENGTH
                || !hasBytes(bytes, 4, 'f', 't', 'y', 'p')) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }

        long declaredSize = readUnsignedInt(bytes, 0);
        int headerSize = STANDARD_BOX_HEADER_LENGTH;
        if (declaredSize == 1) {
            if (bytes.length < LARGE_BOX_HEADER_LENGTH) {
                throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
            }
            declaredSize = readPositiveLong(bytes, 8);
            headerSize = LARGE_BOX_HEADER_LENGTH;
        } else if (declaredSize == 0) {
            // ftyp 뒤에는 실제 이미지 박스가 이어져야 하므로 EOF까지 확장되는 크기는 허용하지 않는다.
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }

        long minimumSize = headerSize + FILE_TYPE_FIELDS_LENGTH;
        if (declaredSize < minimumSize
                || declaredSize > contentLength
                || declaredSize > MAX_FILE_SIZE
                || (declaredSize - minimumSize) % 4 != 0) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }
        return new FileTypeBoxHeader((int) declaredSize, headerSize);
    }

    private static boolean hasSupportedBrand(byte[] bytes, int offset, String... supportedBrands) {
        for (String brand : supportedBrands) {
            if (hasBytes(bytes, offset, brand.charAt(0), brand.charAt(1), brand.charAt(2), brand.charAt(3))) {
                return true;
            }
        }
        return false;
    }

    private static long readUnsignedInt(byte[] bytes, int offset) {
        long value = 0;
        for (int index = 0; index < 4; index++) {
            value = (value << 8) | Byte.toUnsignedInt(bytes[offset + index]);
        }
        return value;
    }

    private static long readPositiveLong(byte[] bytes, int offset) {
        if ((bytes[offset] & 0x80) != 0) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }
        long value = 0;
        for (int index = 0; index < Long.BYTES; index++) {
            value = (value << 8) | Byte.toUnsignedInt(bytes[offset + index]);
        }
        return value;
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

    private record FileTypeBoxHeader(int boxSize, int headerSize) {
    }
}
