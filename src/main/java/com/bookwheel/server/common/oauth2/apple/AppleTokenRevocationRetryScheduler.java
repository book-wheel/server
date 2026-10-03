package com.bookwheel.server.common.oauth2.apple;

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
public class AppleTokenRevocationRetryScheduler {

    private final AppleOAuthCredentialService credentialService;
    private final Clock clock;

    @Scheduled(cron = "${app.oauth2.apple.revocation-cron:30 */5 * * * *}", zone = "Asia/Seoul")
    public void revokeQueuedTokens() {
        int completed = 0;
        for (int batch = 0; batch < credentialService.maxBatchesPerRun(); batch++) {
            List<String> userPKs = credentialService.findDueUserPKs(LocalDateTime.now(clock));
            if (userPKs.isEmpty()) {
                break;
            }
            for (String userPK : userPKs) {
                try {
                    if (credentialService.processOne(userPK)) {
                        completed++;
                    }
                } catch (RuntimeException exception) {
                    log.error("Apple 연동 해제 작업 처리 예외: userPK={}, errorType={}",
                            userPK, exception.getClass().getSimpleName(), exception);
                }
            }
        }
        if (completed > 0) {
            log.info("Apple 연동 해제 작업 정리 완료: completed={}", completed);
        }
    }
}
