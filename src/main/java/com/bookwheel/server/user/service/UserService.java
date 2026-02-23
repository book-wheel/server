package com.bookwheel.server.user.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.jwt.JwtTokenProvider;
import com.bookwheel.server.common.jwt.RefreshToken;
import com.bookwheel.server.common.jwt.RefreshTokenRepository;
import com.bookwheel.server.user.dto.*;
import com.bookwheel.server.user.entity.Role;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public UserResponse signup(UserSignupRequest request) {
        if (!emailService.isVerified(request.mail())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        validateDuplication(request);

        User user = User.builder()
                .userId(request.userId())
                .password(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .mail(request.mail())
                .comment(request.comment())
                .role(Role.USER)
                .build();

        User savedUser = userRepository.save(user);
        log.info("회원가입 완료: userId={}", savedUser.getUserId());

        return UserResponse.from(savedUser);
    }

    @Transactional
    public LoginResponse login(UserLoginRequest request) {
        User user = findByUserIdAndValidateActive(request.userId());

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getUserId(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId(), user.getRole());

        // Redis에 Refresh Token 저장 (ID: Key, 토큰: Value)
        refreshTokenRepository.save(new RefreshToken(user.getUserId(), refreshToken));

        log.info("로그인 성공: userId={}", user.getUserId());

        return LoginResponse.of(user, accessToken, refreshToken);
    }

    private void validateDuplication(UserSignupRequest request) {
        if (userRepository.existsByUserId(request.userId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_USER_ID);
        }
        if (userRepository.existsByMail(request.mail())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
    }

    @Transactional
    public TokenResponse reissue(TokenReissueRequest request) {
        String refreshToken = request.refreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        String userId = jwtTokenProvider.getAuthentication(refreshToken).getName();

        // Redis에서 저장된 토큰 가져오기
        RefreshToken storedToken = refreshTokenRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        // 받은 토큰과 현재 거 동일한지 비교
        if (!storedToken.getRefreshToken().equals(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        User user = findByUserIdAndValidateActive(userId);

        // 새 Access Token 발급
        String newAccessToken = jwtTokenProvider.createAccessToken(userId, user.getRole());

        log.info("토큰 재발급 성공: userId={}", userId);

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .build();
    }

    // 내 정보 조회
    public UserResponse getMyInfo(String userId) {
        User user = findByUserIdAndValidateActive(userId);
        return UserResponse.from(user);
    }

    private User findByUserIdAndValidateActive(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!user.getIsActive()) {
            throw new BusinessException(ErrorCode.INACTIVE_USER);
        }
        return user;
    }

    // 로그아웃
    @Transactional
    public void logout(String userId) {
        refreshTokenRepository.deleteById(userId);
        log.info("로그아웃 완료: userId={}", userId);
    }

    // 회원 탈퇴
    @Transactional
    public void withdraw(String userId, UserWithdrawRequest request) {
        User user = findByUserIdAndValidateActive(userId);

        // 비밀번호 재확인
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        // 회원 상태 비활성화 (Soft Delete)
        user.deactivate();

        refreshTokenRepository.deleteById(userId);

        log.info("회원 탈퇴 완료: userId={}", userId);
    }
}