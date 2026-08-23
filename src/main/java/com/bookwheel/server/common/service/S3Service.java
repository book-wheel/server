package com.bookwheel.server.common.service;

import com.bookwheel.server.common.dto.S3ObjectMetadata;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.util.PathNormalizer;
import com.bookwheel.server.community.dto.PostImagePresignedRequest;
import com.bookwheel.server.community.dto.PostImagePresignedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
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
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    private static final int MAX_POST_IMAGE_COUNT = 5;
    private static final Map<String, String> POST_IMAGE_CONTENT_TYPE_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "heic", "image/heic",
            "heif", "image/heif"
    );

    public String getPresignedGetUrl(String objectKey) {
        // 1. 방어 로직 - 키 값 없거나 공백이면 예외 발생
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 2. 키 정규화 - 앞 뒤 의미 없는 공백 제거
        String normalizedKey = objectKey.trim();
        if (normalizedKey.startsWith("/")) {
            normalizedKey = normalizedKey.substring(1);
        }

        //3. S3 객체 조최 요청 생성 - 대상 커빗과 파일 키 지정
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(normalizedKey)
                .build();

        // 4. Presigned URL 발급 요청 생성 - 5분 제한
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .getObjectRequest(getObjectRequest)
                .build();
        // 5. 최종 URL 생성 및 반환
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    public String getPresignedUrl(String prefix, String fileName) {
        // 중복 방지를 위해 고유한 파일 경로 생성
        // 결과: "attachments/123e4567..._file.jpg"
        String normalizedPrefix = PathNormalizer.normalizeSegment(prefix);
        String normalizedFileName = PathNormalizer.normalizeFileName(fileName);
        if (normalizedPrefix.equals("profiles")
                || normalizedPrefix.startsWith("profiles/")
                || normalizedPrefix.equals("profiles-temp")
                || normalizedPrefix.startsWith("profiles-temp/")) {
            // 프로필 이미지는 userPK 귀속·용량 제한이 적용되는 전용 API로만 발급한다.
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        String objectKey = normalizedPrefix + "/" + UUID.randomUUID() + "_" + normalizedFileName;

        // S3에 올릴 객체 정보 세팅
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        // 5분간 유효한 Presigned URL 요청 객체 생성
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .putObjectRequest(putObjectRequest)
                .build();

        // 최종 URL 문자열 반환
        return s3Presigner.presignPutObject(presignRequest).url().toString();
    }

    public String getPresignedPutUrl(String objectKey, String contentType, long contentLength) {
        if (objectKey == null || objectKey.isBlank()
                || contentType == null || contentType.isBlank()
                || contentLength <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .putObjectRequest(putObjectRequest)
                .build();

        try {
            return s3Presigner.presignPutObject(presignRequest).url().toString();
        } catch (RuntimeException exception) {
            log.error("S3 Presigned PUT URL 발급 실패: key={}, error={}", objectKey, exception.getMessage());
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR);
        }
    }

    public S3ObjectMetadata getObjectMetadata(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_FILE_KEY);
        }

        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
            return new S3ObjectMetadata(response.contentLength(), response.contentType(), response.eTag());
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
            }
            log.error("S3 객체 정보 조회 실패: key={}, status={}, error={}",
                    objectKey, exception.statusCode(), exception.getMessage());
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR);
        } catch (RuntimeException exception) {
            log.error("S3 객체 정보 조회 실패: key={}, error={}", objectKey, exception.getMessage());
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR);
        }
    }

    public byte[] getObjectSignature(String objectKey, String expectedETag, int length) {
        if (objectKey == null || objectKey.isBlank()
                || expectedETag == null || expectedETag.isBlank()
                || length <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        try {
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .ifMatch(expectedETag)
                    .range("bytes=0-" + (length - 1))
                    .build());
            return response.asByteArray();
        } catch (S3Exception exception) {
            throw mapObjectValidationException(objectKey, exception);
        } catch (RuntimeException exception) {
            log.error("S3 객체 시그니처 조회 실패: key={}, error={}", objectKey, exception.getMessage());
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR);
        }
    }

    public void copyObjectIfUnchanged(String sourceKey, String destinationKey, String expectedETag) {
        if (sourceKey == null || sourceKey.isBlank()
                || destinationKey == null || destinationKey.isBlank()
                || expectedETag == null || expectedETag.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        try {
            s3Client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(bucket)
                    .sourceKey(sourceKey)
                    .destinationBucket(bucket)
                    .destinationKey(destinationKey)
                    .copySourceIfMatch(expectedETag)
                    .build());
        } catch (S3Exception exception) {
            throw mapObjectValidationException(sourceKey, exception);
        } catch (RuntimeException exception) {
            log.error("S3 객체 확정 복사 실패: sourceKey={}, destinationKey={}, error={}",
                    sourceKey, destinationKey, exception.getMessage());
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR);
        }
    }

    public int deleteObjectsOlderThan(String prefix, Instant cutoff) {
        if (prefix == null || prefix.isBlank() || cutoff == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        int deletedObjectCount = 0;
        String continuationToken = null;
        try {
            do {
                ListObjectsV2Response response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix(prefix)
                        .continuationToken(continuationToken)
                        .build());
                for (software.amazon.awssdk.services.s3.model.S3Object object : response.contents()) {
                    if (object.lastModified() != null
                            && object.lastModified().isBefore(cutoff)
                            && deleteObject(object.key())) {
                        deletedObjectCount++;
                    }
                }
                continuationToken = Boolean.TRUE.equals(response.isTruncated())
                        ? response.nextContinuationToken()
                        : null;
            } while (continuationToken != null);
            return deletedObjectCount;
        } catch (S3Exception exception) {
            log.error("S3 만료 객체 정리 실패: prefix={}, status={}, error={}",
                    prefix, exception.statusCode(), exception.getMessage());
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR);
        } catch (RuntimeException exception) {
            log.error("S3 만료 객체 정리 실패: prefix={}, error={}", prefix, exception.getMessage());
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR);
        }
    }

    private BusinessException mapObjectValidationException(String objectKey, S3Exception exception) {
        if (exception.statusCode() == 404) {
            return new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        if (exception.statusCode() == 412) {
            return new BusinessException(ErrorCode.FILE_CHANGED_DURING_VALIDATION);
        }
        log.error("S3 객체 검증 실패: key={}, status={}, error={}",
                objectKey, exception.statusCode(), exception.getMessage());
        return new BusinessException(ErrorCode.FILE_UPLOAD_ERROR);
    }

    public PostImagePresignedResponse getPostPresignedUrls(
            String bookId,
            List<PostImagePresignedRequest.FileInfo> files
    ) {
        if (files == null || files.isEmpty() || files.size() > MAX_POST_IMAGE_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        String prefix = "posts/" + PathNormalizer.normalizeSegment(bookId);
        List<PostImagePresignedResponse.PresignedInfo> presignedInfos = files.stream().map(file -> {

            PostImageUploadInfo imageUploadInfo = validatePostImageUploadInfo(file);
            String objectKey = prefix + "/" + UUID.randomUUID() + "_image." + imageUploadInfo.extension();

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(imageUploadInfo.contentType())
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(5))
                    .putObjectRequest(putObjectRequest).build();

            String presignedUrl = s3Presigner.presignPutObject(presignRequest).url().toString();

            return new PostImagePresignedResponse.PresignedInfo(
                    presignedUrl,
                    objectKey,
                    imageUploadInfo.contentType()
            );
        }).toList();
        return new PostImagePresignedResponse(presignedInfos);
    }

    private PostImageUploadInfo validatePostImageUploadInfo(PostImagePresignedRequest.FileInfo file) {
        if (file == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        String extension = normalizePostImageExtension(file.fileExtension());
        String contentType = normalizePostImageContentType(file.contentType());
        String expectedContentType = POST_IMAGE_CONTENT_TYPE_BY_EXTENSION.get(extension);
        if (expectedContentType == null || !expectedContentType.equals(contentType)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }

        return new PostImageUploadInfo(extension, contentType);
    }

    private String normalizePostImageExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }

        String normalizedExtension = extension.trim().toLowerCase(Locale.ROOT);
        if (normalizedExtension.startsWith(".")) {
            normalizedExtension = normalizedExtension.substring(1);
        }

        if (normalizedExtension.isBlank()
                || normalizedExtension.contains(".")
                || normalizedExtension.contains("/")
                || normalizedExtension.contains("\\")) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }

        return normalizedExtension;
    }

    private String normalizePostImageContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }

        String normalizedContentType = contentType.trim();
        if (!contentType.equals(normalizedContentType)
                || !contentType.equals(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
        }

        return normalizedContentType;
    }

    private record PostImageUploadInfo(
            String extension,
            String contentType
    ) {}

    public boolean deleteObject(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return false;
        }

        try {
            // 정규화
            String normalizedKey = objectKey.trim();
            if (normalizedKey.startsWith("/")) {
                normalizedKey = normalizedKey.substring(1);
            }

            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(normalizedKey)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("S3 객체 삭제 완료: key={}", normalizedKey);
            return true;
        } catch (Exception e) {
            log.error("S3 객체 삭제 실패: key={}, error={}", objectKey, e.getMessage());
            return false;
        }
    }
}
