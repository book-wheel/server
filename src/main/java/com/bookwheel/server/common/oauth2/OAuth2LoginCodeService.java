package com.bookwheel.server.common.oauth2;

import com.bookwheel.server.common.auth.AuthRole;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.jwt.JwtTokenProvider;
import com.bookwheel.server.common.jwt.RefreshToken;
import com.bookwheel.server.common.jwt.RefreshTokenRepository;
import com.bookwheel.server.user.dto.OAuth2LoginCodeExchangeRequest;
import com.bookwheel.server.user.dto.OAuth2TokenResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class OAuth2LoginCodeService {

    private static final String CODE_PREFIX = "OAUTH2:LOGIN_CODE:";
    private static final Duration CODE_TTL = Duration.ofMinutes(1);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    public String issue(String userPK, AuthRole role, boolean isFirstLogin, String codeChallenge) {
        if (!OAuth2Pkce.isValidCodeChallenge(codeChallenge)) {
            throw new BusinessException(ErrorCode.INVALID_PKCE_VALUE);
        }

        String code = generateCode();
        StoredLoginCode storedLoginCode = new StoredLoginCode(userPK, role, isFirstLogin, codeChallenge);

        try {
            String value = objectMapper.writeValueAsString(storedLoginCode);
            redisTemplate.opsForValue().set(CODE_PREFIX + code, value, CODE_TTL);
            return code;
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public OAuth2TokenResponse exchange(OAuth2LoginCodeExchangeRequest request) {
        if (!OAuth2Pkce.isValidCodeVerifier(request.codeVerifier())) {
            throw new BusinessException(ErrorCode.INVALID_PKCE_VALUE);
        }

        String value = redisTemplate.opsForValue().getAndDelete(CODE_PREFIX + request.code());
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_OAUTH2_LOGIN_CODE);
        }

        StoredLoginCode storedLoginCode = readStoredLoginCode(value);
        String actualCodeChallenge = OAuth2Pkce.createS256CodeChallenge(request.codeVerifier());
        if (!constantTimeEquals(storedLoginCode.codeChallenge(), actualCodeChallenge)) {
            throw new BusinessException(ErrorCode.INVALID_OAUTH2_LOGIN_CODE);
        }

        String accessToken = jwtTokenProvider.createAccessToken(storedLoginCode.userPK(), storedLoginCode.role());
        String refreshToken = jwtTokenProvider.createRefreshToken(storedLoginCode.userPK(), storedLoginCode.role());
        refreshTokenRepository.save(new RefreshToken(storedLoginCode.userPK(), refreshToken));

        return new OAuth2TokenResponse(accessToken, refreshToken, storedLoginCode.isFirstLogin());
    }

    private StoredLoginCode readStoredLoginCode(String value) {
        try {
            return objectMapper.readValue(value, StoredLoginCode.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INVALID_OAUTH2_LOGIN_CODE);
        }
    }

    private String generateCode() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private record StoredLoginCode(
            String userPK,
            AuthRole role,
            boolean isFirstLogin,
            String codeChallenge
    ) {
    }
}
