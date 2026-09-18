package com.bookwheel.server.user.service;

import com.bookwheel.server.common.jwt.RefreshTokenRepository;
import com.bookwheel.server.common.jwt.AccessTokenRevocationService;
import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.notification.service.NotificationPreferenceService;
import com.bookwheel.server.user.dto.UserWithdrawRequest;
import com.bookwheel.server.user.entity.SocialType;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.event.UserDeactivatedEvent;
import com.bookwheel.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserServiceWithdrawalTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private S3Service s3Service;
    @Mock private MemberRepository memberRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private NotificationPreferenceService notificationPreferenceService;
    @Mock private UserConsentService userConsentService;
    @Mock private S3DeletionQueueService s3DeletionQueueService;
    @Mock private AccessTokenRevocationService accessTokenRevocationService;
    @Spy private Clock clock = Clock.fixed(
            Instant.parse("2026-09-17T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("login")
                .password("encoded-password")
                .nickname("nickname")
                .mail("user@example.com")
                .socialType(SocialType.NONE)
                .profileImageKey("profiles/user/profile.jpg")
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("탈퇴 시 30일 후 삭제를 예약하고 동의 증빙은 별도 보관한다")
    void withdrawSchedulesPurgeAndConsentRetention() {
        String userPK = user.getId();
        LocalDateTime withdrawalRequestedAt = LocalDateTime.of(2026, 9, 17, 10, 0);
        given(userRepository.findById(userPK)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password", "encoded-password")).willReturn(true);
        given(memberRepository.existsByUser_IdAndMemberStatus(userPK, MemberStatus.ACTIVE)).willReturn(false);

        userService.withdraw(userPK, new UserWithdrawRequest("password"));

        assertThat(user.getIsActive()).isFalse();
        assertThat(user.getWithdrawalRequestedAt()).isEqualTo(withdrawalRequestedAt);
        assertThat(user.getPurgeAt()).isEqualTo(withdrawalRequestedAt.plusDays(30));
        then(userConsentService).should().scheduleRetention(userPK, withdrawalRequestedAt);
        then(refreshTokenRepository).should().deleteById(userPK);
        then(notificationPreferenceService).should().clearExpoPushTokenForUser(userPK);
        then(accessTokenRevocationService).should().revokeAllAccessTokens(userPK);
        then(s3DeletionQueueService).should().enqueue(userPK, "profiles/user/profile.jpg");
        then(s3Service).should(never()).deleteObject("profiles/user/profile.jpg");

        ArgumentCaptor<UserDeactivatedEvent> eventCaptor = ArgumentCaptor.forClass(UserDeactivatedEvent.class);
        then(eventPublisher).should().publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().userPK()).isEqualTo(userPK);
    }
}
