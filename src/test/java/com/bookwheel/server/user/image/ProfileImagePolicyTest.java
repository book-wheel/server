package com.bookwheel.server.user.image;

import com.bookwheel.server.common.dto.S3ObjectMetadata;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
