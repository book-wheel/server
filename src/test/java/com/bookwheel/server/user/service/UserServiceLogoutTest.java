package com.bookwheel.server.user.service;

import com.bookwheel.server.common.jwt.RefreshTokenRepository;
import com.bookwheel.server.notification.service.NotificationPreferenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class UserServiceLogoutTest {

    @Test
    @DisplayName("로그아웃하면 Refresh Token과 Expo Push Token을 함께 해제한다")
    void logoutClearsRefreshAndExpoPushTokens() {
        RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
        NotificationPreferenceService preferenceService = mock(NotificationPreferenceService.class);
        UserService userService = new UserService(
                null,
                null,
                null,
                null,
                refreshTokenRepository,
                null,
                null,
                null,
                null,
                null,
                preferenceService,
                null
        );

        userService.logout("userPK");

        then(refreshTokenRepository).should().deleteById("userPK");
        then(preferenceService).should().clearExpoPushTokenForUser("userPK");
    }
}
