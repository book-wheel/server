package com.bookwheel.server.user.service;

import com.bookwheel.server.user.dto.UserSignupRequest;
import com.bookwheel.server.user.entity.ConsentSource;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import com.bookwheel.server.common.jwt.JwtTokenProvider;
import com.bookwheel.server.common.jwt.RefreshTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserServiceSignupTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private UserConsentService userConsentService;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Spy private Clock clock = Clock.fixed(
            Instant.parse("2026-09-17T01:00:00Z"), ZoneId.of("Asia/Seoul")
    );

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("일반 회원가입과 같은 트랜잭션에서 동의 증빙을 저장한다")
    void signupRecordsConsentEvidence() {
        UserSignupRequest request = new UserSignupRequest(
                "bookwheel123",
                "Password1!",
                "user@example.com",
                true,
                true,
                false,
                "terms-2026-09",
                "privacy-2026-09",
                null
        );
        given(emailService.isVerified(request.mail())).willReturn(true);
        given(userRepository.findByLoginId(request.loginId())).willReturn(Optional.empty());
        given(userRepository.findByMailAndSocialType(request.mail(), com.bookwheel.server.user.entity.SocialType.NONE))
                .willReturn(Optional.empty());
        given(passwordEncoder.encode(request.password())).willReturn("encoded-password");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(jwtTokenProvider.createOnboardingToken(any(String.class))).willReturn("onboarding-token");

        var response = userService.signup(request);

        assertThat(response.loginId()).isEqualTo(request.loginId());
        assertThat(response.accessToken()).isEqualTo("onboarding-token");
        assertThat(response.refreshToken()).isNull();
        then(userConsentService).should().recordRequiredConsents(
                response.userPK(),
                "user@example.com",
                true,
                "terms-2026-09",
                true,
                "privacy-2026-09",
                false,
                null,
                ConsentSource.LOCAL_SIGNUP
        );
    }

    @Test
    @DisplayName("프로필 설정 전 일반 로그인은 Refresh Token 없이 온보딩 토큰만 발급한다")
    void incompleteProfileLoginIssuesOnlyOnboardingToken() {
        User user = User.builder()
                .loginId("bookwheel123")
                .password("encoded-password")
                .nickname("USER_temp")
                .mail("user@example.com")
                .isActive(true)
                .build();
        given(userRepository.findByLoginId("bookwheel123")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("Password1!", "encoded-password")).willReturn(true);
        given(jwtTokenProvider.createOnboardingToken(user.getId())).willReturn("onboarding-token");

        var response = userService.login(new com.bookwheel.server.user.dto.UserLoginRequest(
                "bookwheel123", "Password1!"
        ));

        assertThat(response.accessToken()).isEqualTo("onboarding-token");
        assertThat(response.refreshToken()).isNull();
        then(refreshTokenRepository).shouldHaveNoInteractions();
    }
}
