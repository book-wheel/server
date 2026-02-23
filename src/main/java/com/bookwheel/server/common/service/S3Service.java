package com.bookwheel.server.common.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Presigner s3Presigner;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

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
}