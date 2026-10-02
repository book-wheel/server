package com.bookwheel.server.common.jwt;

import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UserAuthenticationStatusServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-02T00:00:00Z"), ZoneOffset.UTC);
    private final LocalDateTime now = LocalDateTime.now(clock);
    private final UserRepository users = mock(UserRepository.class);
    private final UserAuthenticationStatusService service = new UserAuthenticationStatusService(users, clock);

    @Test
    void allowsActiveUser() {
        assertThat(canAuthenticate(User.builder().build())).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"THREE_DAYS", "SEVEN_DAYS", "PERMANENT"})
    void rejectsBannedUser(String banType) {
        User user = User.builder().build();
        user.applyBan(banType, now);
        assertThat(canAuthenticate(user)).isFalse();
    }

    @Test
    void allowsExpiredBan() {
        User user = User.builder().build();
        user.applyBan("THREE_DAYS", now.minusDays(3).minusSeconds(1));
        assertThat(canAuthenticate(user)).isTrue();
    }

    @Test
    void rejectsBanAtExactExpiryConsistentWithUserStatus() {
        User user = User.builder().build();
        user.applyBan("THREE_DAYS", now.minusDays(3));
        assertThat(canAuthenticate(user)).isFalse();
    }

    @Test
    void rejectsWithdrawnUser() {
        assertThat(canAuthenticate(User.builder().isActive(false).build())).isFalse();
    }

    @Test
    void rejectsMissingUser() {
        when(users.findById("missing-user-pk")).thenReturn(Optional.empty());
        assertThat(service.canAuthenticate("missing-user-pk")).isFalse();
    }

    private boolean canAuthenticate(User user) {
        String userPK = user.getId();
        when(users.findById(userPK)).thenReturn(Optional.of(user));
        return service.canAuthenticate(userPK);
    }
}
