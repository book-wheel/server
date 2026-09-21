package com.bookwheel.server.user.scheduler;

import com.bookwheel.server.user.service.S3DeletionQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3DeletionRetryScheduler {

    private final S3DeletionQueueService queueService;
    private final Clock clock;

    @Scheduled(cron = "${user.retention.s3-cleanup-cron:0 */5 * * * *}", zone = "Asia/Seoul")
    public void cleanupQueuedObjects() {
        int completed = 0;
        for (int batch = 0; batch < queueService.maxBatchesPerRun(); batch++) {
            List<Long> taskPKs = queueService.findDueTaskPKs(LocalDateTime.now(clock));
            if (taskPKs.isEmpty()) {
                break;
            }
            for (Long taskPK : taskPKs) {
                try {
                    if (queueService.processOne(taskPK)) {
                        completed++;
                    }
                } catch (RuntimeException exception) {
                    log.error("S3 삭제 재시도 작업 처리 예외: taskPK={}, error={}",
                            taskPK, exception.getMessage(), exception);
                }
            }
        }
        if (completed > 0) {
            log.info("S3 삭제 재시도 작업 정리 완료: completed={}", completed);
        }
    }
}
