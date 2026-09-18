package com.bookwheel.server.user.scheduler;

import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import com.bookwheel.server.user.service.AbandonedOnboardingCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AbandonedOnboardingCleanupScheduler {

    private static final int BATCH_SIZE = 100;
    private static final int RETENTION_DAYS = 7;

    private final UserRepository userRepository;
    private final AbandonedOnboardingCleanupService cleanupService;
    private final Clock clock;

    @Scheduled(cron = "${user.onboarding.cleanup-cron:0 45 3 * * *}", zone = "Asia/Seoul")
    public void cleanupAbandonedOnboardingAccounts() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(RETENTION_DAYS);
        LocalDateTime cursorCreatedAt = null;
        String cursorUserPK = null;
        int deleted = 0;

        while (true) {
            List<User> candidates = userRepository.findAbandonedOnboardingCandidates(
                    cutoff,
                    cursorCreatedAt,
                    cursorUserPK,
                    PageRequest.of(0, BATCH_SIZE)
            );
            if (candidates.isEmpty()) {
                break;
            }
            for (User candidate : candidates) {
                try {
                    if (cleanupService.deleteIfStillAbandoned(candidate.getId(), cutoff)) {
                        deleted++;
                    }
                } catch (RuntimeException exception) {
                    log.error("미완료 온보딩 계정 삭제 실패: userPK={}, error={}",
                            candidate.getId(), exception.getMessage(), exception);
                }
            }
            User lastCandidate = candidates.get(candidates.size() - 1);
            cursorCreatedAt = lastCandidate.getCreatedAt();
            cursorUserPK = lastCandidate.getId();
        }

        if (deleted > 0) {
            log.info("미완료 온보딩 계정 정리 완료: deleted={}", deleted);
        }
    }
}
