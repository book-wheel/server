package com.bookwheel.server.common.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.dto.PostImagePresignedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Presigner s3Presigner;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;
    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "webp");
    private static final Duration DEFAULT_GET_PRESIGN_DURATION = Duration.ofMinutes(1);

    public String getPresignedUrl(String prefix, String fileName) {
        // 중복 방지를 위해 고유한 파일 경로 생성
        // 결과: "profiles/123e4567..._cat.jpg"
        String objectKey = prefix + "/" + UUID.randomUUID() + "_" + fileName;

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

    public PostImagePresignedResponse getPostPresignedUrls(String bookId, List<String> fileExtensions) {
        String prefix = "posts/" + bookId;
        List<PostImagePresignedResponse.PresignedInfo> presignedInfos = fileExtensions.stream().map(ext -> {

            String normalizedExt = ext.toLowerCase().replace(".", "");
            if (!ALLOWED_EXTENSIONS.contains(normalizedExt)) {
                throw new BusinessException(ErrorCode.INVALID_FILE_FORMAT);
            }
            String objectKey = prefix + "/" + UUID.randomUUID() + "_image." + normalizedExt;

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .putObjectRequest(putObjectRequest).build();

            String presignedUrl = s3Presigner.presignPutObject(presignRequest).url().toString();

            return new PostImagePresignedResponse.PresignedInfo(presignedUrl, objectKey);
        }).toList();
        return new PostImagePresignedResponse(presignedInfos);
    }

    public String getPresignedGetUrl(String objectKey) {
        return getPresignedGetUrl(objectKey, DEFAULT_GET_PRESIGN_DURATION);
    }

    public String getPresignedGetUrl(String objectKey, Duration duration) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(duration)
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }
}
