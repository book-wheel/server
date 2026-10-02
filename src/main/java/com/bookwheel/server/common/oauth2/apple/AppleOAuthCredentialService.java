package com.bookwheel.server.common.oauth2.apple;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppleOAuthCredentialService {

    private static final int TASK_BATCH_SIZE = 100;
    private static final int MAX_BATCHES_PER_RUN = 10;
    private static final Duration INITIAL_RETRY_DELAY = Duration.ofMinutes(5);
    private static final Duration MAX_RETRY_DELAY = Duration.ofHours(24);

    private final AppleOAuthCredentialRepository credentialRepository;
    private final AppleRefreshTokenCipher tokenCipher;
    private final AppleTokenRevocationClient revocationClient;
    private final Clock clock;

    @Transactional
    public void store(String userPK, String refreshToken) {
        AppleRefreshTokenCipher.EncryptionResult encrypted = tokenCipher.encrypt(userPK, refreshToken);
        LocalDateTime now = LocalDateTime.now(clock);
        // 토큰 저장과 탈퇴 시 연동 해제 예약을 동일한 행 잠금으로 직렬화한다.
        AppleOAuthCredential credential = credentialRepository.findByUserPKForUpdate(userPK).orElse(null);
        if (credential == null) {
            credential = new AppleOAuthCredential(
                    userPK,
                    encrypted.encryptedToken(),
                    encrypted.keyVersion(),
                    now
            );
        } else {
            credential.updateToken(encrypted.encryptedToken(), encrypted.keyVersion(), now);
        }
        credentialRepository.save(credential);
    }

    @Transactional
    public void requestRevocation(String userPK) {
        AppleOAuthCredential credential = credentialRepository.findByUserPKForUpdate(userPK).orElse(null);
        if (credential == null) {
            log.warn("Apple refresh token이 없어 연동 해제를 예약할 수 없습니다: userPK={}", userPK);
            return;
        }
        credential.requestRevocation(LocalDateTime.now(clock));
        log.info("Apple 연동 해제 예약 완료: userPK={}", userPK);
    }

    @Transactional(readOnly = true)
    public List<String> findDueUserPKs(LocalDateTime now) {
        return credentialRepository.findDueUserPKs(now, PageRequest.of(0, TASK_BATCH_SIZE));
    }

    public int maxBatchesPerRun() {
        return MAX_BATCHES_PER_RUN;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processOne(String userPK) {
        AppleOAuthCredential credential = credentialRepository.findByUserPKForUpdate(userPK).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (credential == null
                || !credential.isRevocationRequested()
                || credential.getNextAttemptAt() == null
                || credential.getNextAttemptAt().isAfter(now)) {
            return false;
        }

        try {
            String refreshToken = tokenCipher.decrypt(
                    userPK,
                    credential.getEncryptionKeyVersion(),
                    credential.getEncryptedRefreshToken()
            );
            revocationClient.revokeRefreshToken(refreshToken);
            credentialRepository.delete(credential);
            log.info("Apple 연동 해제 완료: userPK={}", userPK);
            return true;
        } catch (RuntimeException exception) {
            Duration retryDelay = retryDelay(credential.getAttemptCount());
            credential.recordFailure(now, now.plus(retryDelay), safeError(exception));
            log.warn("Apple 연동 해제 재시도 예약: userPK={}, attempt={}, nextAttemptAt={}, errorType={}",
                    userPK,
                    credential.getAttemptCount(),
                    credential.getNextAttemptAt(),
                    exception.getClass().getSimpleName());
            return false;
        }
    }

    private Duration retryDelay(int previousAttemptCount) {
        int shift = Math.min(previousAttemptCount, 8);
        Duration calculated = INITIAL_RETRY_DELAY.multipliedBy(1L << shift);
        return calculated.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : calculated;
    }

    private String safeError(RuntimeException exception) {
        if (exception instanceof RestClientResponseException responseException) {
            return "HTTP " + responseException.getStatusCode().value();
        }
        return exception.getClass().getSimpleName();
    }
}
