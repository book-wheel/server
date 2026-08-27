package com.bookwheel.server.common.service;

import com.bookwheel.server.common.dto.S3ObjectMetadata;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.dto.PostImagePresignedRequest;
import com.bookwheel.server.community.dto.PostImagePresignedResponse;
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
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

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
    @DisplayName("게시물 이미지 Presigned URL 발급 시 HEIC/HEIF 확장자를 허용하고 소문자로 정규화한다")
    void getPostPresignedUrls_AcceptsHeicAndHeifExtensions() throws MalformedURLException {
        String isbn = "9788966263158";
        given(presignedPutObjectRequest.url()).willReturn(new URL("https://s3.example.com/upload"));
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .willReturn(presignedPutObjectRequest);

        PostImagePresignedResponse response = s3Service.getPostPresignedUrls(isbn, List.of(
                new PostImagePresignedRequest.FileInfo(".HEIC", "image/heic"),
                new PostImagePresignedRequest.FileInfo("HEIF", "image/heif")
        ));

        assertThat(response.presignedUrls()).hasSize(2);
        assertThat(response.presignedUrls().get(0).presignedUrl()).isEqualTo("https://s3.example.com/upload");
        assertThat(response.presignedUrls().get(0).contentType()).isEqualTo("image/heic");
        assertThat(response.presignedUrls().get(0).objectKey())
                .startsWith("posts/" + isbn + "/")
                .endsWith("_image.heic");
        assertThat(response.presignedUrls().get(1).contentType()).isEqualTo("image/heif");
        assertThat(response.presignedUrls().get(1).objectKey())
                .startsWith("posts/" + isbn + "/")
                .endsWith("_image.heif");

        ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        then(s3Presigner).should(org.mockito.Mockito.times(2)).presignPutObject(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(request -> request.putObjectRequest().contentType())
                .containsExactly("image/heic", "image/heif");
    }

    @Test
    @DisplayName("게시글 이미지 Presigned URL 발급 시 확장자와 MIME 타입이 일치하지 않으면 거부한다")
    void getPostPresignedUrls_RejectsMismatchedContentType() {
        String isbn = "9788966263158";

        assertThatThrownBy(() -> s3Service.getPostPresignedUrls(isbn, List.of(
                new PostImagePresignedRequest.FileInfo("heic", "image/jpeg")
        )))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE_FORMAT);
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

    @Test
    @DisplayName("prefix 아래 객체를 페이지 단위로 조회하고 기준 시각보다 오래된 객체만 삭제한다")
    void deleteObjectsOlderThan_PaginatesAndDeletesOnlyExpiredObjects() {
        Instant cutoff = Instant.parse("2026-08-03T00:00:00Z");
        ListObjectsV2Response firstPage = ListObjectsV2Response.builder()
                .contents(
                        S3Object.builder()
                                .key("chat-temp/old.png")
                                .lastModified(cutoff.minusSeconds(1))
                                .build(),
                        S3Object.builder()
                                .key("chat-temp/recent.png")
                                .lastModified(cutoff.plusSeconds(1))
                                .build()
                )
                .isTruncated(true)
                .nextContinuationToken("next-page")
                .build();
        ListObjectsV2Response secondPage = ListObjectsV2Response.builder()
                .contents(S3Object.builder()
                        .key("chat-temp/older.png")
                        .lastModified(cutoff.minusSeconds(60))
                        .build())
                .isTruncated(false)
                .build();
        given(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .willReturn(firstPage, secondPage);

        int deletedObjectCount = s3Service.deleteObjectsOlderThan("chat-temp/", cutoff);

        assertThat(deletedObjectCount).isEqualTo(2);
        ArgumentCaptor<ListObjectsV2Request> captor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        then(s3Client).should(org.mockito.Mockito.times(2)).listObjectsV2(captor.capture());
        assertThat(captor.getAllValues().get(0).prefix()).isEqualTo("chat-temp/");
        assertThat(captor.getAllValues().get(0).continuationToken()).isNull();
        assertThat(captor.getAllValues().get(1).continuationToken()).isEqualTo("next-page");

        ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        then(s3Client).should(org.mockito.Mockito.times(2)).deleteObject(deleteCaptor.capture());
        assertThat(deleteCaptor.getAllValues())
                .extracting(DeleteObjectRequest::key)
                .containsExactly("chat-temp/old.png", "chat-temp/older.png");
    }

    @Test
    @DisplayName("S3 객체 삭제 실패를 호출자가 관측할 수 있도록 false를 반환한다")
    void deleteObject_S3Failure_ReturnsFalse() {
        given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .willThrow(new IllegalStateException("S3 unavailable"));

        assertThat(s3Service.deleteObject("profiles-temp/user-pk/image.png")).isFalse();
    }

    @Test
    @DisplayName("공통 Presigned URL API는 프로필 예약 prefix 사용을 거부한다")
    void getPresignedUrl_ProfileReservedPrefix_Rejects() {
        assertThatThrownBy(() -> s3Service.getPresignedUrl("profiles-temp/user-pk", "profile.png"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }
}
