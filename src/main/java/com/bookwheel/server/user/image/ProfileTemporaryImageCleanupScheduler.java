package com.bookwheel.server.user.image;

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
public class ProfileTemporaryImageCleanupScheduler {

    private final S3Service s3Service;
    private final Clock clock;
    private final Duration retention;

    public ProfileTemporaryImageCleanupScheduler(
            S3Service s3Service,
            Clock clock,
            @Value("${profile.image.temp-retention:PT24H}") Duration retention
    ) {
        if (retention == null || retention.isZero() || retention.isNegative()) {
            throw new IllegalArgumentException("프로필 임시 이미지 보존 기간은 0보다 커야 합니다.");
        }
        this.s3Service = s3Service;
        this.clock = clock;
        this.retention = retention;
    }

    @Scheduled(cron = "${profile.image.temp-cleanup-cron:0 30 3 * * *}", zone = "Asia/Seoul")
    public void deleteExpiredTemporaryImages() {
        Instant cutoff = Instant.now(clock).minus(retention);
        try {
            int deletedObjectCount = s3Service.deleteObjectsOlderThan(
                    ProfileImagePolicy.TEMPORARY_PREFIX,
                    cutoff
            );
            if (deletedObjectCount > 0) {
                log.info("만료된 프로필 임시 이미지 정리 완료: count={}", deletedObjectCount);
            }
        } catch (RuntimeException exception) {
            log.error("프로필 임시 이미지 정리 실패: error={}", exception.getMessage());
        }
    }
}
