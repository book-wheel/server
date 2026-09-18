package com.bookwheel.server.user.service;

import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.image.ProfileImagePolicy;
import com.bookwheel.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AbandonedOnboardingCleanupService {

    private final UserRepository userRepository;
    private final UserConsentService userConsentService;
    private final S3DeletionQueueService s3DeletionQueueService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deleteIfStillAbandoned(String userPK, LocalDateTime cutoff) {
        User user = userRepository.findByUserPKForUpdate(userPK).orElse(null);
        if (user == null
                || !Boolean.TRUE.equals(user.getIsActive())
                || Boolean.TRUE.equals(user.getIsProfileSet())
                || user.getCreatedAt() == null
                || user.getCreatedAt().isAfter(cutoff)) {
            return false;
        }

        if (ProfileImagePolicy.isStoredProfileObjectKey(user.getProfileImageKey())) {
            s3DeletionQueueService.enqueue(userPK, user.getProfileImageKey());
        }
        // 프로필 완료 여부와 무관하게 동의 행위 증빙은 계정 삭제 시점부터 3년간 별도 보관한다.
        userConsentService.scheduleRetentionFromAccountDeletion(userPK);
        userRepository.delete(user);
        userRepository.flush();
        log.info("미완료 온보딩 계정 삭제 완료: userPK={}", userPK);
        return true;
    }
}
