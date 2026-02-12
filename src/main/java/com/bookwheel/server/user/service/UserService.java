package com.bookwheel.server.user.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.jwt.JwtTokenProvider;
import com.bookwheel.server.common.jwt.RefreshToken;
import com.bookwheel.server.common.jwt.RefreshTokenRepository;
import com.bookwheel.server.user.dto.*;
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
        // 이메일 인증 여부 확인
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

        // Access Token 발급
        String accessToken = jwtTokenProvider.createAccessToken(user.getUserId());

        // Refresh Token 발급
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId());

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

        // 1. 토큰 유효성 검사 (위조된 건지 확인)
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN); // 혹은 401 에러
        }

        // 2. 토큰에서 유저 ID 뽑아내기
        String userId = jwtTokenProvider.getAuthentication(refreshToken).getName();

        // 3. Redis 금고에서 저장된 토큰 가져오기
        RefreshToken storedToken = refreshTokenRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND)); // "로그아웃 되셨는데요?"

        // 4. 내가 받은 토큰이랑 금고에 있는 거랑 똑같은지 비교
        if (!storedToken.getRefreshToken().equals(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN); // "누가 토큰 훔쳐서 쓰는 중?"
        }

        // 5. 다 통과했으니 새 Access Token 발급!
        String newAccessToken = jwtTokenProvider.createAccessToken(userId);

        // (선택) Refresh Token도 새로 발급해서 보안 강화 (Rotate)
        // String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);
        // refreshTokenRepository.save(new RefreshToken(userId, newRefreshToken));

        log.info("토큰 재발급 성공: userId={}", userId);

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken) // 일단 기존 Refresh Token 그대로 반환 (Rotate 안 할 경우)
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
}