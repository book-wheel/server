package com.bookwheel.server.chat.image;

import com.bookwheel.server.common.dto.S3ObjectMetadata;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.bookwheel.server.chat.image.ChatImagePolicy.MAX_FILE_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatImagePolicyTest {

    private static final String ETAG = "\"image-etag\"";
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
    };
    private static final byte[] HEIC_SIGNATURE = {
            0, 0, 0, 24, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c'
    };
    private static final byte[] HEIF_SIGNATURE = {
            0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'i', 'f', '1'
    };

    @ParameterizedTest
    @CsvSource({
            "photo.jpg,image/jpeg,jpg",
            "photo.JPEG,image/jpeg,jpeg",
            "photo.png,image/png,png",
            "photo.webp,image/webp,webp",
            "photo.HEIC,image/heic,heic",
            "photo.heif,image/heif,heif"
    })
    @DisplayName("지원 확장자와 MIME 타입 조합을 허용한다")
    void validateUploadRequest_AcceptsSupportedCombination(
            String fileName,
            String contentType,
            String expectedExtension
    ) {
        ChatImagePolicy.ValidatedImage result = ChatImagePolicy.validateUploadRequest(
                fileName,
                contentType,
                100L
        );

        assertThat(result.extension()).isEqualTo(expectedExtension);
        assertThat(result.contentType()).isEqualTo(contentType);
    }

    @Test
    @DisplayName("지원하지 않는 이미지 확장자를 거부한다")
    void validateUploadRequest_RejectsUnsupportedExtension() {
        assertErrorCode(
                () -> ChatImagePolicy.validateUploadRequest("photo.gif", "image/gif", 100L),
                ErrorCode.INVALID_FILE_FORMAT
        );
    }

    @Test
    @DisplayName("확장자와 MIME 타입이 일치하지 않으면 거부한다")
    void validateUploadRequest_RejectsMismatchedContentType() {
        assertErrorCode(
                () -> ChatImagePolicy.validateUploadRequest("photo.png", "image/jpeg", 100L),
                ErrorCode.INVALID_FILE_FORMAT
        );
    }

    @Test
    @DisplayName("서명할 MIME 타입은 정규화된 소문자 형식만 허용한다")
    void validateUploadRequest_RejectsNonCanonicalContentType() {
        assertErrorCode(
                () -> ChatImagePolicy.validateUploadRequest("photo.png", "IMAGE/PNG", 100L),
                ErrorCode.INVALID_FILE_FORMAT
        );
    }

    @Test
    @DisplayName("5MB를 초과한 이미지 크기를 거부한다")
    void validateUploadRequest_RejectsOversizedFile() {
        assertErrorCode(
                () -> ChatImagePolicy.validateUploadRequest("photo.png", "image/png", MAX_FILE_SIZE + 1),
                ErrorCode.FILE_SIZE_EXCEEDED
        );
    }

    @Test
    @DisplayName("현재 채팅방과 사용자 경로로 발급한 이미지 키를 허용한다")
    void validateOwnedTemporaryObjectKey_AcceptsOwnedKey() {
        String imageKey = "chat-temp/chat-room-1/user-pk/550e8400-e29b-41d4-a716-446655440000.png";

        org.assertj.core.api.Assertions.assertThatCode(() ->
                ChatImagePolicy.validateOwnedTemporaryObjectKey(imageKey, "chat-room-1", "user-pk")
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("다른 사용자의 경로를 가진 이미지 키를 거부한다")
    void validateOwnedTemporaryObjectKey_RejectsAnotherUsersKey() {
        String imageKey = "chat-temp/chat-room-1/another-user-pk/550e8400-e29b-41d4-a716-446655440000.png";

        assertErrorCode(
                () -> ChatImagePolicy.validateOwnedTemporaryObjectKey(imageKey, "chat-room-1", "user-pk"),
                ErrorCode.INVALID_FILE_KEY
        );
    }

    @Test
    @DisplayName("임시 이미지 키를 변경 불가능한 최종 이미지 키로 변환한다")
    void toFinalObjectKey_ReplacesTemporaryPrefix() {
        String temporaryKey = "chat-temp/chat-room-1/user-pk/550e8400-e29b-41d4-a716-446655440000.png";

        String finalKey = ChatImagePolicy.toFinalObjectKey(temporaryKey);

        assertThat(finalKey)
                .isEqualTo("chat/chat-room-1/user-pk/550e8400-e29b-41d4-a716-446655440000.png");
    }

    @Test
    @DisplayName("실제 업로드 객체의 크기가 5MB를 초과하면 거부한다")
    void validateUploadedObject_RejectsOversizedObject() {
        String imageKey = "chat-temp/chat-room-1/user-pk/550e8400-e29b-41d4-a716-446655440000.png";

        assertErrorCode(
                () -> ChatImagePolicy.validateUploadedObject(
                        imageKey,
                        new S3ObjectMetadata(MAX_FILE_SIZE + 1, "image/png", ETAG),
                        PNG_SIGNATURE
                ),
                ErrorCode.FILE_SIZE_EXCEEDED
        );
    }

    @Test
    @DisplayName("실제 업로드 객체의 MIME 타입이 확장자와 다르면 거부한다")
    void validateUploadedObject_RejectsMismatchedContentType() {
        String imageKey = "chat-temp/chat-room-1/user-pk/550e8400-e29b-41d4-a716-446655440000.webp";

        assertErrorCode(
                () -> ChatImagePolicy.validateUploadedObject(
                        imageKey,
                        new S3ObjectMetadata(100L, "image/png", ETAG),
                        PNG_SIGNATURE
                ),
                ErrorCode.INVALID_FILE_FORMAT
        );
    }

    @Test
    @DisplayName("Content-Type이 일치해도 실제 PNG 시그니처가 아니면 거부한다")
    void validateUploadedObject_RejectsInvalidBinarySignature() {
        String imageKey = "chat-temp/chat-room-1/user-pk/550e8400-e29b-41d4-a716-446655440000.png";

        assertErrorCode(
                () -> ChatImagePolicy.validateUploadedObject(
                        imageKey,
                        new S3ObjectMetadata(100L, "image/png", ETAG),
                        "not-an-image".getBytes()
                ),
                ErrorCode.INVALID_FILE_FORMAT
        );
    }

    @Test
    @DisplayName("확장자, 메타데이터, 바이너리 시그니처가 모두 일치하면 허용한다")
    void validateUploadedObject_AcceptsValidImage() {
        String imageKey = "chat-temp/chat-room-1/user-pk/550e8400-e29b-41d4-a716-446655440000.png";

        org.assertj.core.api.Assertions.assertThatCode(() ->
                ChatImagePolicy.validateUploadedObject(
                        imageKey,
                        new S3ObjectMetadata(100L, "image/png", ETAG),
                        PNG_SIGNATURE
                )
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("실제 업로드된 HEIC 객체의 MIME 타입과 시그니처가 일치하면 허용한다")
    void validateUploadedObject_AcceptsValidHeicImage() {
        String imageKey = "chat-temp/chat-room-1/user-pk/550e8400-e29b-41d4-a716-446655440000.heic";

        org.assertj.core.api.Assertions.assertThatCode(() ->
                ChatImagePolicy.validateUploadedObject(
                        imageKey,
                        new S3ObjectMetadata(100L, "image/heic", ETAG),
                        HEIC_SIGNATURE
                )
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("실제 업로드된 HEIF 객체의 MIME 타입과 시그니처가 일치하면 허용한다")
    void validateUploadedObject_AcceptsValidHeifImage() {
        String imageKey = "chat-temp/chat-room-1/user-pk/550e8400-e29b-41d4-a716-446655440000.heif";

        org.assertj.core.api.Assertions.assertThatCode(() ->
                ChatImagePolicy.validateUploadedObject(
                        imageKey,
                        new S3ObjectMetadata(100L, "image/heif", ETAG),
                        HEIF_SIGNATURE
                )
        ).doesNotThrowAnyException();
    }

    private void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }
}
