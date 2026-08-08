package com.bookwheel.server.common.oauth2;

import com.bookwheel.server.common.auth.AuthRole;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.jwt.JwtTokenProvider;
import com.bookwheel.server.common.jwt.RefreshToken;
import com.bookwheel.server.common.jwt.RefreshTokenRepository;
import com.bookwheel.server.user.dto.OAuth2LoginCodeExchangeRequest;
import com.bookwheel.server.user.dto.OAuth2TokenResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OAuth2LoginCodeServiceTest {

    private static final String CODE_VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CODE_CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private JwtTokenProvider jwtTokenProvider;
    private RefreshTokenRepository refreshTokenRepository;
    private OAuth2LoginCodeService loginCodeService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        loginCodeService = new OAuth2LoginCodeService(
                redisTemplate,
                new ObjectMapper(),
                jwtTokenProvider,
                refreshTokenRepository
        );
    }

    @Test
    @DisplayName("유효한 코드와 verifier를 교환하면 JWT를 발급하고 Refresh Token을 저장한다")
    void exchangesOneTimeCodeForTokens() {
        IssuedCode issuedCode = issueLoginCode();
        given(valueOperations.getAndDelete(issuedCode.redisKey())).willReturn(issuedCode.storedValue());
        given(jwtTokenProvider.createAccessToken("user-pk", AuthRole.USER)).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken("user-pk", AuthRole.USER)).willReturn("refresh-token");

        OAuth2TokenResponse response = loginCodeService.exchange(
                new OAuth2LoginCodeExchangeRequest(issuedCode.code(), CODE_VERIFIER)
        );

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.isFirstLogin()).isTrue();
        verify(valueOperations).getAndDelete(issuedCode.redisKey());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUserPK()).isEqualTo("user-pk");
        assertThat(captor.getValue().getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("일회용 코드는 한 번 교환한 뒤 다시 사용할 수 없다")
    void rejectsReusedCode() {
        IssuedCode issuedCode = issueLoginCode();
        given(valueOperations.getAndDelete(issuedCode.redisKey()))
                .willReturn(issuedCode.storedValue(), (String) null);
        given(jwtTokenProvider.createAccessToken("user-pk", AuthRole.USER)).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken("user-pk", AuthRole.USER)).willReturn("refresh-token");
        OAuth2LoginCodeExchangeRequest request = new OAuth2LoginCodeExchangeRequest(
                issuedCode.code(),
                CODE_VERIFIER
        );

        loginCodeService.exchange(request);

        assertThatThrownBy(() -> loginCodeService.exchange(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_OAUTH2_LOGIN_CODE);
    }

    @Test
    @DisplayName("code verifier가 일치하지 않으면 JWT를 발급하지 않는다")
    void rejectsWrongCodeVerifier() {
        IssuedCode issuedCode = issueLoginCode();
        given(valueOperations.getAndDelete(issuedCode.redisKey())).willReturn(issuedCode.storedValue());
        String wrongVerifier = "a".repeat(43);

        assertThatThrownBy(() -> loginCodeService.exchange(
                new OAuth2LoginCodeExchangeRequest(issuedCode.code(), wrongVerifier)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_OAUTH2_LOGIN_CODE);

        verify(jwtTokenProvider, never()).createAccessToken(anyString(), eq(AuthRole.USER));
        verify(refreshTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private IssuedCode issueLoginCode() {
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);

        String code = loginCodeService.issue("user-pk", AuthRole.USER, true, CODE_CHALLENGE);

        verify(valueOperations).set(keyCaptor.capture(), valueCaptor.capture(), eq(Duration.ofMinutes(1)));
        return new IssuedCode(code, keyCaptor.getValue(), valueCaptor.getValue());
    }

    private record IssuedCode(String code, String redisKey, String storedValue) {
    }
}
