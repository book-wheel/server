package com.bookwheel.server.user.service;

import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.user.entity.S3DeletionTask;
import com.bookwheel.server.user.repository.S3DeletionTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3DeletionQueueService {

    private static final int TASK_BATCH_SIZE = 100;
    private static final int MAX_BATCHES_PER_RUN = 10;
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofMinutes(5);
    private static final Duration MAX_RETRY_DELAY = Duration.ofHours(24);

    private final S3DeletionTaskRepository taskRepository;
    private final S3Service s3Service;
    private final Clock clock;

    @Transactional
    public void enqueue(String userPK, String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return;
        }
        String normalizedObjectKey = objectKey.strip();
        String objectKeyHash = sha256(normalizedObjectKey);
        if (taskRepository.existsByObjectKeyHash(objectKeyHash)) {
            return;
        }
        taskRepository.save(new S3DeletionTask(
                userPK,
                normalizedObjectKey,
                objectKeyHash,
                LocalDateTime.now(clock)
        ));
    }

    @Transactional
    public void enqueueAll(String userPK, Iterable<String> objectKeys) {
        for (String objectKey : objectKeys) {
            enqueue(userPK, objectKey);
        }
    }

    @Transactional(readOnly = true)
    public List<Long> findDueTaskPKs(LocalDateTime now) {
        return taskRepository.findDueTaskPKs(now, PageRequest.of(0, TASK_BATCH_SIZE));
    }

    public int maxBatchesPerRun() {
        return MAX_BATCHES_PER_RUN;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processOne(Long taskPK) {
        S3DeletionTask task = taskRepository.findByTaskPKForUpdate(taskPK).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (task == null || task.getNextAttemptAt().isAfter(now)) {
            return false;
        }

        if (s3Service.deleteObject(task.getObjectKey())) {
            taskRepository.delete(task);
            log.info("S3 삭제 재시도 작업 완료: taskPK={}, userPK={}", taskPK, task.getUserPK());
            return true;
        }

        Duration retryDelay = retryDelay(task.getAttemptCount());
        task.recordFailure(now, now.plus(retryDelay), "S3 deleteObject returned false");
        log.warn("S3 삭제 재시도 예약: taskPK={}, userPK={}, attempt={}, nextAttemptAt={}",
                taskPK, task.getUserPK(), task.getAttemptCount(), task.getNextAttemptAt());
        return false;
    }

    private Duration retryDelay(int previousAttemptCount) {
        int shift = Math.min(previousAttemptCount, 8);
        Duration calculated = INITIAL_RETRY_DELAY.multipliedBy(1L << shift);
        return calculated.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : calculated;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("S3 객체 키 해시를 생성할 수 없습니다.", exception);
        }
    }
}
