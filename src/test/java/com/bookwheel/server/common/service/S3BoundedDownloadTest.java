package com.bookwheel.server.common.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class S3BoundedDownloadTest {

    @Mock private S3Client s3Client;
    private S3Service service;

    @BeforeEach
    void setUp() {
        service = new S3Service(null, s3Client);
        ReflectionTestUtils.setField(service, "bucket", "test-bucket");
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4})
    void acceptsFilesUpToLimitAndClosesStream(int size) {
        TrackingInputStream input = new TrackingInputStream(size);
        stubDownload(input, (long) size);

        assertThat(service.getObjectBytes("image.png", 4)).hasSize(size);
        assertThat(input.closed).isTrue();
        assertThat(input.aborted).isFalse();
    }

    @Test
    void rejectsOversizedMetadataWithoutReadingBody() {
        TrackingInputStream input = new TrackingInputStream(100);
        stubDownload(input, 100L);

        assertSizeExceeded();

        assertThat(input.available()).isEqualTo(100);
        assertThat(input.aborted).isTrue();
        assertThat(input.closed).isTrue();
    }

    @Test
    void limitsActualReadsWhenMetadataIsMissing() {
        assertBoundedRead(null);
    }

    @Test
    void limitsActualReadsWhenMetadataUnderstatesSize() {
        assertBoundedRead(1L);
    }

    @Test
    void abortsOnReadFailureAndMapsError() {
        boolean[] aborted = {false};
        InputStream input = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("read failed");
            }
        };
        given(s3Client.getObject(any(GetObjectRequest.class))).willReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                AbortableInputStream.create(input, () -> aborted[0] = true)));

        assertThatThrownBy(() -> service.getObjectBytes("image.png", 4))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FILE_UPLOAD_ERROR);
        assertThat(aborted[0]).isTrue();
    }

    @Test
    void preservesNotFoundError() {
        given(s3Client.getObject(any(GetObjectRequest.class)))
                .willThrow(S3Exception.builder().statusCode(404).build());

        assertThatThrownBy(() -> service.getObjectBytes("image.png", 4))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FILE_NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, Integer.MAX_VALUE, Long.MAX_VALUE})
    void rejectsInvalidLimitsBeforeDownloading(long limit) {
        assertThatThrownBy(() -> service.getObjectBytes("image.png", limit))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        verifyNoInteractions(s3Client);
    }

    private void assertBoundedRead(Long contentLength) {
        TrackingInputStream input = new TrackingInputStream(100);
        stubDownload(input, contentLength);

        assertSizeExceeded();

        // 최대 크기 4바이트 + 초과 감지용 1바이트만 소비한다.
        assertThat(input.available()).isEqualTo(95);
        assertThat(input.aborted).isTrue();
        assertThat(input.closed).isTrue();
    }

    private void assertSizeExceeded() {
        assertThatThrownBy(() -> service.getObjectBytes("image.png", 4))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED);
    }

    private void stubDownload(TrackingInputStream input, Long contentLength) {
        given(s3Client.getObject(any(GetObjectRequest.class))).willReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().contentLength(contentLength).build(),
                AbortableInputStream.create(input, () -> input.aborted = true)));
    }

    private static class TrackingInputStream extends ByteArrayInputStream {
        private boolean aborted;
        private boolean closed;

        TrackingInputStream(int size) {
            super(new byte[size]);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
