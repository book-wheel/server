package com.bookwheel.server.common.service;

import com.bookwheel.server.common.dto.S3ObjectMetadata;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    private static final String BUCKET = "bookwheel-test";

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Client s3Client;

    @Mock
    private PresignedPutObjectRequest presignedPutObjectRequest;

    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        s3Service = new S3Service(s3Presigner, s3Client);
        ReflectionTestUtils.setField(s3Service, "bucket", BUCKET);
    }

    @Test
    @DisplayName("Content-Type을 포함해 Presigned PUT URL을 발급한다")
    void getPresignedPutUrl_IncludesContentType() throws MalformedURLException {
        String objectKey = "chat/chat-room-1/user-pk/550e8400-e29b-41d4-a716-446655440000.png";
        given(presignedPutObjectRequest.url()).willReturn(new URL("https://s3.example.com/upload"));
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .willReturn(presignedPutObjectRequest);

        String result = s3Service.getPresignedPutUrl(objectKey, "image/png", 123_456L);

        assertThat(result).isEqualTo("https://s3.example.com/upload");
        ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        then(s3Presigner).should().presignPutObject(captor.capture());
        PutObjectPresignRequest request = captor.getValue();
        assertThat(request.signatureDuration()).isEqualTo(Duration.ofMinutes(5));
        assertThat(request.putObjectRequest().bucket()).isEqualTo(BUCKET);
        assertThat(request.putObjectRequest().key()).isEqualTo(objectKey);
        assertThat(request.putObjectRequest().contentType()).isEqualTo("image/png");
        assertThat(request.putObjectRequest().contentLength()).isEqualTo(123_456L);
    }

    @Test
    @DisplayName("S3 HeadObject 응답에서 실제 파일 크기와 MIME 타입을 반환한다")
    void getObjectMetadata_ReturnsHeadObjectMetadata() {
        String objectKey = "chat/chat-room-1/user-pk/550e8400-e29b-41d4-a716-446655440000.png";
        given(s3Client.headObject(any(HeadObjectRequest.class))).willReturn(HeadObjectResponse.builder()
                .contentLength(123_456L)
                .contentType("image/png")
                .eTag("\"image-etag\"")
                .build());

        S3ObjectMetadata metadata = s3Service.getObjectMetadata(objectKey);

        assertThat(metadata.contentLength()).isEqualTo(123_456L);
        assertThat(metadata.contentType()).isEqualTo("image/png");
        assertThat(metadata.eTag()).isEqualTo("\"image-etag\"");
        ArgumentCaptor<HeadObjectRequest> captor = ArgumentCaptor.forClass(HeadObjectRequest.class);
        then(s3Client).should().headObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo(objectKey);
    }

    @Test
    @DisplayName("S3에 객체가 없으면 FILE_NOT_FOUND 오류를 반환한다")
    void getObjectMetadata_MapsNotFoundError() {
        String objectKey = "chat/chat-room-1/user-pk/550e8400-e29b-41d4-a716-446655440000.png";
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willThrow(S3Exception.builder().statusCode(404).message("Not Found").build());

        assertThatThrownBy(() -> s3Service.getObjectMetadata(objectKey))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FILE_NOT_FOUND);
    }

    @Test
    @DisplayName("Presigned URL 발급 실패를 FILE_UPLOAD_ERROR로 변환한다")
    void getPresignedPutUrl_MapsPresignerFailure() {
        String objectKey = "chat/chat-room-1/user-pk/550e8400-e29b-41d4-a716-446655440000.png";
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .willThrow(new IllegalStateException("presigner failure"));

        assertThatThrownBy(() -> s3Service.getPresignedPutUrl(objectKey, "image/png", 100L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FILE_UPLOAD_ERROR);
    }

    @Test
    @DisplayName("같은 ETag 객체의 앞부분만 조회해 이미지 시그니처를 반환한다")
    void getObjectSignature_UsesETagAndRange() {
        String objectKey = "chat-temp/chat-room-1/user-pk/550e8400-e29b-41d4-a716-446655440000.png";
        byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47};
        given(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .willReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), signature));

        byte[] result = s3Service.getObjectSignature(objectKey, "\"image-etag\"", 12);

        assertThat(result).containsExactly(signature);
        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        then(s3Client).should().getObjectAsBytes(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo(objectKey);
        assertThat(captor.getValue().ifMatch()).isEqualTo("\"image-etag\"");
        assertThat(captor.getValue().range()).isEqualTo("bytes=0-11");
    }

    @Test
    @DisplayName("검증한 ETag와 일치하는 임시 객체만 최종 경로로 복사한다")
    void copyObjectIfUnchanged_UsesSourceETagCondition() {
        String sourceKey = "chat-temp/chat-room-1/user-pk/550e8400-e29b-41d4-a716-446655440000.png";
        String destinationKey = "chat/chat-room-1/user-pk/550e8400-e29b-41d4-a716-446655440000.png";

        s3Service.copyObjectIfUnchanged(sourceKey, destinationKey, "\"image-etag\"");

        ArgumentCaptor<CopyObjectRequest> captor = ArgumentCaptor.forClass(CopyObjectRequest.class);
        then(s3Client).should().copyObject(captor.capture());
        CopyObjectRequest request = captor.getValue();
        assertThat(request.sourceBucket()).isEqualTo(BUCKET);
        assertThat(request.sourceKey()).isEqualTo(sourceKey);
        assertThat(request.destinationBucket()).isEqualTo(BUCKET);
        assertThat(request.destinationKey()).isEqualTo(destinationKey);
        assertThat(request.copySourceIfMatch()).isEqualTo("\"image-etag\"");
    }

    @Test
    @DisplayName("시그니처 조회 중 객체가 바뀌면 충돌 오류로 변환한다")
    void getObjectSignature_MapsPreconditionFailure() {
        String objectKey = "chat-temp/chat-room-1/user-pk/550e8400-e29b-41d4-a716-446655440000.png";
        given(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .willThrow(S3Exception.builder().statusCode(412).message("Precondition Failed").build());

        assertThatThrownBy(() -> s3Service.getObjectSignature(objectKey, "\"old-etag\"", 12))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FILE_CHANGED_DURING_VALIDATION);
    }
}
