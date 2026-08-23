package com.bookwheel.server.chat.image;

import com.bookwheel.server.common.service.S3Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
public class ChatTemporaryImageCleanupScheduler {

    private static final String TEMPORARY_IMAGE_PREFIX = "chat-temp/";

    private final S3Service s3Service;
    private final Clock clock;
    private final Duration retention;

    public ChatTemporaryImageCleanupScheduler(
            S3Service s3Service,
            Clock clock,
            @Value("${chat.image.temp-retention:PT24H}") Duration retention
    ) {
        if (retention == null || retention.isZero() || retention.isNegative()) {
            throw new IllegalArgumentException("채팅 임시 이미지 보존 기간은 0보다 커야 합니다.");
        }
        this.s3Service = s3Service;
        this.clock = clock;
        this.retention = retention;
    }

    @Scheduled(cron = "${chat.image.temp-cleanup-cron:0 0 3 * * *}", zone = "Asia/Seoul")
    public void deleteExpiredTemporaryImages() {
        Instant cutoff = Instant.now(clock).minus(retention);
        try {
            int deletedObjectCount = s3Service.deleteObjectsOlderThan(
                    TEMPORARY_IMAGE_PREFIX,
                    cutoff
            );
            if (deletedObjectCount > 0) {
                log.info("만료된 채팅 임시 이미지 정리 완료: count={}", deletedObjectCount);
            }
        } catch (RuntimeException exception) {
            // 한 번의 조회 실패가 이후 스케줄 실행까지 중단시키지 않도록 기록하고 종료한다.
            log.error("채팅 임시 이미지 정리 실패: error={}", exception.getMessage());
        }
    }
}
