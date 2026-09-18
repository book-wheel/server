package com.bookwheel.server.user.scheduler;

import com.bookwheel.server.user.repository.UserRepository;
import com.bookwheel.server.user.entity.SocialType;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.service.UserConsentService;
import com.bookwheel.server.user.service.UserDataPurgeTransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class UserDataRetentionSchedulerTest {

    @Test
    @DisplayName("만료된 탈퇴 회원과 동의 증빙을 정리한다")
    void cleanupExpiredUserDataPurgesUsersAndConsents() {
        UserRepository userRepository = mock(UserRepository.class);
        UserDataPurgeTransactionService purgeService = mock(UserDataPurgeTransactionService.class);
        UserConsentService consentService = mock(UserConsentService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-09-17T01:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        UserDataRetentionScheduler scheduler = new UserDataRetentionScheduler(
                userRepository,
                purgeService,
                consentService,
                clock
        );
        User first = withdrawnUser("first", LocalDateTime.of(2026, 8, 1, 0, 0));
        User second = withdrawnUser("second", LocalDateTime.of(2026, 8, 2, 0, 0));
        given(userRepository.findPurgeCandidates(
                any(LocalDateTime.class), isNull(), isNull(), any(Pageable.class)))
                .willReturn(List.of(first, second));
        given(userRepository.findPurgeCandidates(
                any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.eq(second.getPurgeAt()),
                org.mockito.ArgumentMatchers.eq(second.getId()),
                any(Pageable.class)))
                .willReturn(List.of());
        given(purgeService.purgeIfDue(first.getId())).willReturn(true);
        given(purgeService.purgeIfDue(second.getId())).willReturn(true);
        given(consentService.deleteExpiredConsents()).willReturn(1L);

        scheduler.cleanupExpiredUserData();

        then(purgeService).should().purgeIfDue(first.getId());
        then(purgeService).should().purgeIfDue(second.getId());
        then(consentService).should().deleteExpiredConsents();
    }

    @Test
    @DisplayName("앞선 회원 삭제가 실패해도 커서를 전진시켜 다음 묶음을 처리한다")
    void cleanupExpiredUserDataDoesNotStarveLaterCandidates() {
        UserRepository userRepository = mock(UserRepository.class);
        UserDataPurgeTransactionService purgeService = mock(UserDataPurgeTransactionService.class);
        UserConsentService consentService = mock(UserConsentService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-09-17T01:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        UserDataRetentionScheduler scheduler = new UserDataRetentionScheduler(
                userRepository,
                purgeService,
                consentService,
                clock
        );
        User failed = withdrawnUser("failed", LocalDateTime.of(2026, 8, 1, 0, 0));
        User later = withdrawnUser("later", LocalDateTime.of(2026, 8, 2, 0, 0));

        given(userRepository.findPurgeCandidates(
                any(LocalDateTime.class), isNull(), isNull(), any(Pageable.class)))
                .willReturn(List.of(failed));
        given(userRepository.findPurgeCandidates(
                any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.eq(failed.getPurgeAt()),
                org.mockito.ArgumentMatchers.eq(failed.getId()),
                any(Pageable.class)))
                .willReturn(List.of(later));
        given(userRepository.findPurgeCandidates(
                any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.eq(later.getPurgeAt()),
                org.mockito.ArgumentMatchers.eq(later.getId()),
                any(Pageable.class)))
                .willReturn(List.of());
        given(purgeService.purgeIfDue(failed.getId())).willThrow(new RuntimeException("temporary failure"));
        given(purgeService.purgeIfDue(later.getId())).willReturn(true);

        scheduler.cleanupExpiredUserData();

        then(purgeService).should().purgeIfDue(failed.getId());
        then(purgeService).should().purgeIfDue(later.getId());
    }

    private User withdrawnUser(String loginId, LocalDateTime withdrawalRequestedAt) {
        User user = User.builder()
                .loginId(loginId)
                .password("password")
                .nickname(loginId)
                .mail(loginId + "@example.com")
                .socialType(SocialType.NONE)
                .isActive(true)
                .build();
        user.deactivate(withdrawalRequestedAt, withdrawalRequestedAt.plusDays(30));
        return user;
    }
}
