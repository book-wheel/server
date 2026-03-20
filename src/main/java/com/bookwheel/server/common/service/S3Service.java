package com.bookwheel.server.common.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.dto.PostImagePresignedResponse;
import com.bookwheel.server.common.util.PathNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "webp");

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
        // 결과: "profiles/123e4567..._cat.jpg"
        String normalizedPrefix = PathNormalizer.normalizeSegment(prefix);
        String normalizedFileName = PathNormalizer.normalizeFileName(fileName);
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

    public PostImagePresignedResponse getPostPresignedUrls(String bookId, List<String> fileExtensions) {
        String prefix = "posts/" + PathNormalizer.normalizeSegment(bookId);
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

    public void deleteObject(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
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
        } catch (Exception e) {
            log.error("S3 객체 삭제 실패: key={}, error={}", objectKey, e.getMessage());
        }
    }
}