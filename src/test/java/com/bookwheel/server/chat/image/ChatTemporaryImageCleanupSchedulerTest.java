package com.bookwheel.server.chat.image;

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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ChatTemporaryImageCleanupSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-08-04T03:00:00Z");

    @Mock
    private S3Service s3Service;

    @Test
    @DisplayName("보존 기간이 지난 chat-temp 객체를 모두 삭제한다")
    void deleteExpiredTemporaryImages_DeletesExpiredObjects() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        Duration retention = Duration.ofHours(24);
        ChatTemporaryImageCleanupScheduler scheduler = new ChatTemporaryImageCleanupScheduler(
                s3Service,
                clock,
                retention
        );
        Instant cutoff = NOW.minus(retention);
        given(s3Service.findObjectKeysOlderThan("chat-temp/", cutoff))
                .willReturn(List.of("chat-temp/old-1.png", "chat-temp/old-2.png"));

        scheduler.deleteExpiredTemporaryImages();

        then(s3Service).should().deleteObject("chat-temp/old-1.png");
        then(s3Service).should().deleteObject("chat-temp/old-2.png");
    }

    @Test
    @DisplayName("S3 임시 객체 조회가 실패해도 다음 스케줄 실행을 위해 예외를 전파하지 않는다")
    void deleteExpiredTemporaryImages_SwallowsLookupFailure() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        Duration retention = Duration.ofHours(24);
        ChatTemporaryImageCleanupScheduler scheduler = new ChatTemporaryImageCleanupScheduler(
                s3Service,
                clock,
                retention
        );
        given(s3Service.findObjectKeysOlderThan("chat-temp/", NOW.minus(retention)))
                .willThrow(new BusinessException(ErrorCode.FILE_UPLOAD_ERROR));

        assertThatCode(scheduler::deleteExpiredTemporaryImages).doesNotThrowAnyException();

        then(s3Service).shouldHaveNoMoreInteractions();
    }
}
