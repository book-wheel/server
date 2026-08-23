package com.bookwheel.server.user.image;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.service.S3Service;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ProfileTemporaryImageCleanupSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-08-23T03:00:00Z");

    @Mock
    private S3Service s3Service;

    @Test
    @DisplayName("보존 기간이 지난 profiles-temp 객체를 페이지 단위로 정리한다")
    void deleteExpiredTemporaryImages_DeletesExpiredTemporaryObjects() {
        Duration retention = Duration.ofHours(24);
        ProfileTemporaryImageCleanupScheduler scheduler = scheduler(retention);
        Instant cutoff = NOW.minus(retention);
        given(s3Service.deleteObjectsOlderThan("profiles-temp/", cutoff)).willReturn(2);

        scheduler.deleteExpiredTemporaryImages();

        then(s3Service).should().deleteObjectsOlderThan("profiles-temp/", cutoff);
    }

    @Test
    @DisplayName("S3 정리가 실패해도 다음 스케줄 실행을 위해 예외를 전파하지 않는다")
    void deleteExpiredTemporaryImages_SwallowsCleanupFailure() {
        Duration retention = Duration.ofHours(24);
        ProfileTemporaryImageCleanupScheduler scheduler = scheduler(retention);
        given(s3Service.deleteObjectsOlderThan("profiles-temp/", NOW.minus(retention)))
                .willThrow(new BusinessException(ErrorCode.FILE_UPLOAD_ERROR));

        assertThatCode(scheduler::deleteExpiredTemporaryImages).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("프로필 임시 이미지 보존 기간은 0보다 커야 한다")
    void constructor_NonPositiveRetention_RejectsConfiguration() {
        assertThatThrownBy(() -> scheduler(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ProfileTemporaryImageCleanupScheduler scheduler(Duration retention) {
        return new ProfileTemporaryImageCleanupScheduler(
                s3Service,
                Clock.fixed(NOW, ZoneOffset.UTC),
                retention
        );
    }
}
