package com.bookwheel.server.user.image;

import com.bookwheel.server.common.dto.S3ObjectMetadata;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileImagePolicyTest {

    private static final String USER_PK = "user-pk";
    private static final String UUID_VALUE = "550e8400-e29b-41d4-a716-446655440000";

    @Test
    @DisplayName("허용 확장자와 MIME 타입을 검증해 소문자 확장자로 정규화한다")
    void validateUploadRequest_ValidPng_ReturnsNormalizedImage() {
        ProfileImagePolicy.ValidatedImage image = ProfileImagePolicy.validateUploadRequest(
                "profile.PNG",
                "image/png",
                123_456L
        );

        assertThat(image.extension()).isEqualTo("png");
        assertThat(image.contentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("5MB를 초과한 프로필 이미지 업로드를 거부한다")
    void validateUploadRequest_OversizedFile_Rejects() {
        assertThatThrownBy(() -> ProfileImagePolicy.validateUploadRequest(
                "profile.png",
                "image/png",
                ProfileImagePolicy.MAX_FILE_SIZE + 1
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED);
    }

    @Test
    @DisplayName("현재 userPK에 귀속된 UUID 형식의 임시 key만 허용한다")
    void validateOwnedTemporaryObjectKey_OtherUser_Rejects() {
        String otherUsersKey = "profiles-temp/other-user-pk/" + UUID_VALUE + ".png";

        assertThatThrownBy(() -> ProfileImagePolicy.validateOwnedTemporaryObjectKey(otherUsersKey, USER_PK))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE_KEY);
    }

    @Test
    @DisplayName("최종 key는 userPK에 귀속되고 각 확정 시도마다 새로 생성된다")
    void createFinalObjectKey_CreatesUniqueOwnedKeys() {
        String temporaryKey = "profiles-temp/" + USER_PK + "/" + UUID_VALUE + ".png";

        String firstKey = ProfileImagePolicy.createFinalObjectKey(temporaryKey, USER_PK);
        String secondKey = ProfileImagePolicy.createFinalObjectKey(temporaryKey, USER_PK);

        assertThat(firstKey).matches("profiles/" + USER_PK + "/[0-9a-f-]{36}\\.png");
        assertThat(secondKey).matches("profiles/" + USER_PK + "/[0-9a-f-]{36}\\.png");
        assertThat(secondKey).isNotEqualTo(firstKey);
    }

    @Test
    @DisplayName("S3 메타데이터와 파일 시그니처가 일치하는 실제 이미지만 허용한다")
    void validateUploadedObject_ValidPng_Accepts() {
        String objectKey = "profiles-temp/" + USER_PK + "/" + UUID_VALUE + ".png";
        byte[] pngSignature = {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
        };

        assertThatCode(() -> ProfileImagePolicy.validateUploadedObject(
                objectKey,
                new S3ObjectMetadata(123_456L, "image/png", "\"etag\""),
                pngSignature
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("HEIC brand가 compatible brands 뒤쪽에 있어도 정상 파일로 인정한다")
    void validateUploadedObject_HeicCompatibleBrand_Accepts() {
        String objectKey = "profiles-temp/" + USER_PK + "/" + UUID_VALUE + ".heic";
        byte[] fileTypeBox = fileTypeBox(
                "mif1",
                "mif1", "MiHB", "MiHE", "MiPr", "miaf", "MiAB",
                "mif1", "MiHB", "MiHE", "MiPr", "miaf", "MiAB", "heic"
        );
        S3ObjectMetadata metadata = new S3ObjectMetadata(123_456L, "image/heic", "\"etag\"");
        byte[] probe = Arrays.copyOf(fileTypeBox, ProfileImagePolicy.SIGNATURE_PROBE_LENGTH);

        assertThat(ProfileImagePolicy.determineSignatureLength(objectKey, metadata, probe))
                .isEqualTo(fileTypeBox.length);
        assertThatCode(() -> ProfileImagePolicy.validateUploadedObject(
                objectKey,
                metadata,
                fileTypeBox
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("HEIF brand가 compatible brands에만 있어도 정상 파일로 인정한다")
    void validateUploadedObject_HeifCompatibleBrand_Accepts() {
        String objectKey = "profiles-temp/" + USER_PK + "/" + UUID_VALUE + ".heif";
        byte[] fileTypeBox = fileTypeBox("heic", "heic", "mif1");
        S3ObjectMetadata metadata = new S3ObjectMetadata(123_456L, "image/heif", "\"etag\"");

        assertThatCode(() -> ProfileImagePolicy.validateUploadedObject(
                objectKey,
                metadata,
                fileTypeBox
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("선언된 ftyp 크기보다 짧게 읽힌 HEIC 헤더를 거부한다")
    void validateUploadedObject_TruncatedFileTypeBox_Rejects() {
        String objectKey = "profiles-temp/" + USER_PK + "/" + UUID_VALUE + ".heic";
        byte[] fileTypeBox = fileTypeBox("mif1", "mif1", "heic");
        byte[] truncated = Arrays.copyOf(fileTypeBox, fileTypeBox.length - 4);
        S3ObjectMetadata metadata = new S3ObjectMetadata(123_456L, "image/heic", "\"etag\"");

        assertThatThrownBy(() -> ProfileImagePolicy.validateUploadedObject(
                objectKey,
                metadata,
                truncated
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE_FORMAT);
    }

    @Test
    @DisplayName("객체 크기를 벗어난 ftyp box size를 거부한다")
    void determineSignatureLength_FileTypeBoxLargerThanObject_Rejects() {
        String objectKey = "profiles-temp/" + USER_PK + "/" + UUID_VALUE + ".heic";
        byte[] fileTypeBox = fileTypeBox("mif1", "heic");
        ByteBuffer.wrap(fileTypeBox).putInt(1_024);
        S3ObjectMetadata metadata = new S3ObjectMetadata(100L, "image/heic", "\"etag\"");

        assertThatThrownBy(() -> ProfileImagePolicy.determineSignatureLength(
                objectKey,
                metadata,
                fileTypeBox
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE_FORMAT);
    }

    private byte[] fileTypeBox(String majorBrand, String... compatibleBrands) {
        int boxSize = 16 + compatibleBrands.length * 4;
        ByteBuffer buffer = ByteBuffer.allocate(boxSize);
        buffer.putInt(boxSize);
        buffer.put("ftyp".getBytes(StandardCharsets.US_ASCII));
        buffer.put(majorBrand.getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(0);
        for (String compatibleBrand : compatibleBrands) {
            buffer.put(compatibleBrand.getBytes(StandardCharsets.US_ASCII));
        }
        return buffer.array();
    }
}
