package com.bookwheel.server.user.service;

import com.bookwheel.server.common.auth.AuthRole;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.jwt.JwtTokenProvider;
import com.bookwheel.server.common.jwt.RefreshTokenRepository;
import com.bookwheel.server.user.dto.LoginResponse;
import com.bookwheel.server.user.dto.ProfileSetupRequest;
import com.bookwheel.server.user.entity.SocialType;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ProfileSetupTransactionServiceTest {

    private static final String CURRENT_OBJECT_KEY = "profiles/current.png";
    private static final String FINAL_OBJECT_KEY = "profiles/user-pk/new.png";

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserConsentService userConsentService;

    // 정지 상태 판정 시각이 실행 환경 시간대에 흔들리지 않도록 KST로 고정한다.
    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-09-03T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    @InjectMocks
    private ProfileSetupTransactionService transactionService;

    private User user;
    private String userPK;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("login")
                .password("encoded-password")
                .nickname("nickname")
                .mail("user@example.com")
                .socialType(SocialType.NONE)
                .profileImageKey(CURRENT_OBJECT_KEY)
                .isActive(true)
                .build();
        userPK = user.getId();
        lenient().when(userRepository.findByUserPKForUpdate(userPK)).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("행 잠금 후 누락된 프로필 이미지 key를 기존값으로 유지한다")
    void persist_Retain_KeepsCurrentImage() {
        givenSuccessfulTokenIssue();

        ProfileSetupTransactionService.Result result = transactionService.persist(
                userPK,
                new ProfileSetupRequest(null, "updated comment", null),
                ProfileSetupTransactionService.ProfileImageUpdate.retain()
        );

        assertThat(user.getProfileImageKey()).isEqualTo(CURRENT_OBJECT_KEY);
        assertThat(result.response().profileImageKey()).isEqualTo(CURRENT_OBJECT_KEY);
        assertThat(result.previousProfileImageKey()).isEqualTo(CURRENT_OBJECT_KEY);
        then(userRepository).should().findByUserPKForUpdate(userPK);
    }

    @Test
    @DisplayName("Matching final key is retained for legacy client compatibility")
    void persist_RetainIfCurrent_WithMatchingKey_KeepsCurrentImage() {
        givenSuccessfulTokenIssue();

        LoginResponse response = transactionService.persist(
                userPK,
                new ProfileSetupRequest(CURRENT_OBJECT_KEY, "comment", null),
                ProfileSetupTransactionService.ProfileImageUpdate.retainIfCurrent(CURRENT_OBJECT_KEY)
        ).response();

        assertThat(user.getProfileImageKey()).isEqualTo(CURRENT_OBJECT_KEY);
        assertThat(response.profileImageKey()).isEqualTo(CURRENT_OBJECT_KEY);
    }

    @Test
    @DisplayName("A different final key is rejected as an injection attempt")
    void persist_RetainIfCurrent_WithDifferentKey_RejectsInjection() {
        assertThatThrownBy(() -> transactionService.persist(
                userPK,
                new ProfileSetupRequest(FINAL_OBJECT_KEY, "comment", null),
                ProfileSetupTransactionService.ProfileImageUpdate.retainIfCurrent(FINAL_OBJECT_KEY)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_FILE_KEY);

        assertThat(user.getProfileImageKey()).isEqualTo(CURRENT_OBJECT_KEY);
        then(jwtTokenProvider).should(never()).createAccessToken(userPK, AuthRole.USER);
        then(refreshTokenRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("행 잠금 후 빈 프로필 이미지 key를 null로 변경한다")
    void persist_Delete_RemovesCurrentImage() {
        givenSuccessfulTokenIssue();

        LoginResponse response = transactionService.persist(
                userPK,
                new ProfileSetupRequest("", "updated comment", null),
                ProfileSetupTransactionService.ProfileImageUpdate.delete()
        ).response();

        assertThat(user.getProfileImageKey()).isNull();
        assertThat(response.profileImageKey()).isNull();
    }

    @Test
    @DisplayName("행 잠금 후 검증된 최종 프로필 이미지 key로 교체한다")
    void persist_Replace_UsesValidatedFinalKey() {
        givenSuccessfulTokenIssue();

        LoginResponse response = transactionService.persist(
                userPK,
                new ProfileSetupRequest("profiles-temp/ignored-by-transaction-service.png", "comment", null),
                ProfileSetupTransactionService.ProfileImageUpdate.replace(FINAL_OBJECT_KEY)
        ).response();

        assertThat(user.getProfileImageKey()).isEqualTo(FINAL_OBJECT_KEY);
        assertThat(response.profileImageKey()).isEqualTo(FINAL_OBJECT_KEY);
    }

    @Test
    @DisplayName("닉네임 중복 시 이미지 key와 토큰을 변경하지 않고 예외를 전파한다")
    void persist_DuplicateNickname_ThrowsBeforeMutation() {
        given(userRepository.existsByNickname("duplicate")).willReturn(true);

        assertThatThrownBy(() -> transactionService.persist(
                userPK,
                new ProfileSetupRequest(null, "comment", "duplicate"),
                ProfileSetupTransactionService.ProfileImageUpdate.replace(FINAL_OBJECT_KEY)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);

        assertThat(user.getProfileImageKey()).isEqualTo(CURRENT_OBJECT_KEY);
        then(jwtTokenProvider).should(never()).createAccessToken(userPK, AuthRole.USER);
        then(refreshTokenRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("소셜 최초 프로필 설정에서 필수 동의 이력을 저장한다")
    void persist_FirstSocialProfileSetup_RecordsConsent() {
        User socialUser = User.builder()
                .loginId("KAKAO_social")
                .password("random-password")
                .nickname("USER_temp")
                .mail("social@example.com")
                .socialType(SocialType.KAKAO)
                .socialId("social-subject")
                .isActive(true)
                .build();
        String socialUserPK = socialUser.getId();
        given(userRepository.findByUserPKForUpdate(socialUserPK)).willReturn(Optional.of(socialUser));
        given(jwtTokenProvider.createAccessToken(socialUserPK, AuthRole.USER)).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken(socialUserPK, AuthRole.USER)).willReturn("refresh-token");

        ProfileSetupRequest request = new ProfileSetupRequest(
                null,
                "comment",
                "nickname",
                true,
                true,
                false,
                "terms-2026-09",
                "privacy-2026-09",
                null
        );

        transactionService.persist(
                socialUserPK,
                request,
                ProfileSetupTransactionService.ProfileImageUpdate.retain()
        );

        then(userConsentService).should().recordRequiredConsents(
                socialUserPK,
                "social@example.com",
                true,
                "terms-2026-09",
                true,
                "privacy-2026-09",
                false,
                null,
                com.bookwheel.server.user.entity.ConsentSource.SOCIAL_PROFILE_SETUP
        );
        assertThat(socialUser.getIsProfileSet()).isTrue();
    }

    @Test
    @DisplayName("이미 프로필을 완료한 계정은 기존 온보딩 토큰을 다시 사용할 수 없다")
    void persist_CompletedProfile_RejectsReusedOnboardingToken() {
        user.completeProfile();
        ProfileSetupRequest request = new ProfileSetupRequest(null, "comment", "nickname");

        assertThatThrownBy(() -> transactionService.persist(
                userPK,
                request,
                ProfileSetupTransactionService.ProfileImageUpdate.retain(),
                true
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);

        then(jwtTokenProvider).should(never()).createAccessToken(userPK, AuthRole.USER);
    }

    private void givenSuccessfulTokenIssue() {
        given(jwtTokenProvider.createAccessToken(userPK, AuthRole.USER)).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken(userPK, AuthRole.USER)).willReturn("refresh-token");
    }
}
