package com.bookwheel.server.user.scheduler;

import com.bookwheel.server.user.repository.UserRepository;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.service.UserConsentService;
import com.bookwheel.server.user.service.UserDataPurgeTransactionService;
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
public class UserDataRetentionScheduler {

    private static final int PURGE_BATCH_SIZE = 100;

    private final UserRepository userRepository;
    private final UserDataPurgeTransactionService purgeTransactionService;
    private final UserConsentService userConsentService;
    private final Clock clock;

    @Scheduled(cron = "${user.retention.cleanup-cron:0 15 4 * * *}", zone = "Asia/Seoul")
    public void cleanupExpiredUserData() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime cursorPurgeAt = null;
        String cursorUserPK = null;
        int purged = 0;
        while (true) {
            List<User> candidates = userRepository.findPurgeCandidates(
                    now,
                    cursorPurgeAt,
                    cursorUserPK,
                    PageRequest.of(0, PURGE_BATCH_SIZE)
            );
            if (candidates.isEmpty()) {
                break;
            }

            for (User candidate : candidates) {
                try {
                    if (purgeTransactionService.purgeIfDue(candidate.getId())) {
                        purged++;
                    }
                } catch (RuntimeException exception) {
                    log.error("탈퇴 회원 영구 삭제 실패: userPK={}, error={}",
                            candidate.getId(), exception.getMessage(), exception);
                }
            }

            User lastCandidate = candidates.get(candidates.size() - 1);
            cursorPurgeAt = lastCandidate.getPurgeAt();
            cursorUserPK = lastCandidate.getId();
        }

        long expiredConsents = userConsentService.deleteExpiredConsents();
        if (purged > 0 || expiredConsents > 0) {
            log.info("회원 보존 데이터 정리 완료: users={}, consents={}", purged, expiredConsents);
        }
    }
}
