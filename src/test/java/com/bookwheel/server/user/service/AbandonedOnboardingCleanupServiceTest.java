package com.bookwheel.server.user.service;

import com.bookwheel.server.user.entity.SocialType;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class AbandonedOnboardingCleanupServiceTest {

    @Test
    @DisplayName("7일 넘게 프로필을 완료하지 않은 계정은 삭제하고 동의 증빙은 3년 보관을 예약한다")
    void deletesAbandonedAccountAndRetainsConsentEvidence() {
        UserRepository userRepository = mock(UserRepository.class);
        UserConsentService userConsentService = mock(UserConsentService.class);
        S3DeletionQueueService queueService = mock(S3DeletionQueueService.class);
        AbandonedOnboardingCleanupService service = new AbandonedOnboardingCleanupService(
                userRepository, userConsentService, queueService
        );
        User user = User.builder()
                .loginId("incomplete")
                .password("password")
                .nickname("USER_temp")
                .mail("user@example.com")
                .socialType(SocialType.NONE)
                .isActive(true)
                .build();
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 1, 0, 0);
        ReflectionTestUtils.setField(user, "createdAt", createdAt);
        given(userRepository.findByUserPKForUpdate(user.getId())).willReturn(Optional.of(user));

        assertThat(service.deleteIfStillAbandoned(
                user.getId(), LocalDateTime.of(2026, 9, 10, 0, 0)
        )).isTrue();

        then(userConsentService).should().scheduleRetentionFromAccountDeletion(user.getId());
        then(userRepository).should().delete(user);
        then(userRepository).should().flush();
    }
}
