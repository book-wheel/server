package com.bookwheel.server.user.service;

import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.user.entity.S3DeletionTask;
import com.bookwheel.server.user.repository.S3DeletionTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class S3DeletionQueueServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 17, 10, 0);

    private S3DeletionTaskRepository repository;
    private S3Service s3Service;
    private S3DeletionQueueService service;

    @BeforeEach
    void setUp() {
        repository = mock(S3DeletionTaskRepository.class);
        s3Service = mock(S3Service.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-09-17T01:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        service = new S3DeletionQueueService(repository, s3Service, clock);
    }

    @Test
    @DisplayName("객체 키를 원본과 SHA-256 식별값으로 재시도 큐에 저장한다")
    void enqueueStoresDeletionTask() {
        service.enqueue("user-pk", " profiles/user/image.jpg ");

        ArgumentCaptor<S3DeletionTask> captor = ArgumentCaptor.forClass(S3DeletionTask.class);
        then(repository).should().save(captor.capture());
        assertThat(captor.getValue().getObjectKey()).isEqualTo("profiles/user/image.jpg");
        assertThat(captor.getValue().getObjectKeyHash()).hasSize(64);
        assertThat(captor.getValue().getNextAttemptAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("S3 삭제에 실패하면 작업을 남기고 다음 시도를 예약한다")
    void processOneReschedulesFailure() {
        S3DeletionTask task = new S3DeletionTask(
                "user-pk", "profiles/user/image.jpg", "hash", NOW.minusMinutes(1)
        );
        given(repository.findByTaskPKForUpdate(1L)).willReturn(Optional.of(task));
        given(s3Service.deleteObject(task.getObjectKey())).willReturn(false);

        assertThat(service.processOne(1L)).isFalse();

        assertThat(task.getAttemptCount()).isEqualTo(1);
        assertThat(task.getNextAttemptAt()).isEqualTo(NOW.plusMinutes(5));
        then(repository).should(never()).delete(any(S3DeletionTask.class));
    }

    @Test
    @DisplayName("S3 삭제 성공 시에만 재시도 작업을 제거한다")
    void processOneRemovesSuccessfulTask() {
        S3DeletionTask task = new S3DeletionTask(
                "user-pk", "profiles/user/image.jpg", "hash", NOW.minusMinutes(1)
        );
        given(repository.findByTaskPKForUpdate(1L)).willReturn(Optional.of(task));
        given(s3Service.deleteObject(task.getObjectKey())).willReturn(true);

        assertThat(service.processOne(1L)).isTrue();

        then(repository).should().delete(task);
    }
}
