package com.bookwheel.server.user.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.jwt.JwtTokenProvider;
import com.bookwheel.server.common.jwt.RefreshToken;
import com.bookwheel.server.common.jwt.RefreshTokenRepository;
import com.bookwheel.server.user.dto.*;
import com.bookwheel.server.user.entity.Role;
import com.bookwheel.server.user.entity.SocialType;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bookwheel.server.user.dto.ProfileSetupRequest;

import java.util.UUID;

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
    private final SocialUnlinkService socialUnlinkService;
    private final org.springframework.security.oauth2.client.OAuth2AuthorizedClientService authorizedClientService;

    @Transactional
    public UserResponse signup(UserSignupRequest request) {
        if (!emailService.isVerified(request.mail())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        if (!isValidPassword(request.password())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD_FORMAT);
        }

        // 일반 회원가입(NONE) 방식 내에서만 중복 및 재가입 체크
        handleExistingUser(request.userId(), request.mail(), SocialType.NONE);

        // 임시 닉네임 부여 (프로필 설정에서 실제 닉네임 설정)
        String tempNickname = "USER_" + UUID.randomUUID().toString().substring(0, 8);

        User user = User.builder()
                .userId(request.userId())
                .password(passwordEncoder.encode(request.password()))
                .nickname(tempNickname)
                .mail(request.mail())
                .role(Role.USER)
                .socialType(SocialType.NONE)
                .isActive(true)
                .build();

        User savedUser = userRepository.save(user);
        log.info("일반 회원가입 1단계 완료: userId={}, tempNickname={}", savedUser.getUserId(), tempNickname);

        return UserResponse.from(savedUser);
    }

    private boolean isValidPassword(String password) {
        String regex = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,20}$";
        return password != null && password.matches(regex);
    }

    @Transactional
    public LoginResponse setupProfile(String userId, ProfileSetupRequest request) {
        User user = findByIdAndValidateActive(userId);

        // 닉네임 중복 체크 및 업데이트 (사용자가 입력한 경우에만)
        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            // 현재 닉네임과 다를 때만 중복 검증 수행
            if (!user.getNickname().equals(request.getNickname())) {
                if (userRepository.existsByNickname(request.getNickname())) {
                    throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
                }
                user.updateNickname(request.getNickname());
            }
        }

        // 전달받은 S3 URL을 그대로 저장
        if (request.getProfileImageUrl() != null) {
            user.updateProfileImage(request.getProfileImageUrl());
        }

        user.updateComment(request.getComment());
        user.completeProfile(); // 프로필 설정 완료

        log.info("프로필 설정 완료 (Stage 2): userId={}, nickname={}", userId, user.getNickname());

        // 최종 로그인 응답 반환 (메인 페이지 이동용 토큰 포함)
        return getLoginResponse(user);
    }

    @Transactional
    public LoginResponse login(UserLoginRequest request) {
        User user = findByUserIdAndValidateActive(request.userId());

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        return getLoginResponse(user);
    }

    public boolean isNicknameDuplicate(String nickname) {
        return userRepository.existsByNickname(nickname);
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

        User user = findByIdAndValidateActive(userId);

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
        User user = findByIdAndValidateActive(userId);
        return UserResponse.from(user);
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
        // 유저 조회 및 활성 상태 검증
        User user = findByIdAndValidateActive(userId);
        // [TODO] 추후 탈퇴하려는 회원이 가입된 모임이 있는지 확인하는 로직 추가 필요

        if (user.getSocialType() == SocialType.NONE) {
            if (request == null || request.password() == null ||
                    !passwordEncoder.matches(request.password(), user.getPassword())) {
                throw new BusinessException(ErrorCode.INVALID_PASSWORD);
            }
        }

        // 디버깅 - 현재 시큐리티 세션에 기록된 진짜 이름을 확인
        log.info("회원 탈퇴 처리 시작 - ID: {}", userId);
        log.info("탈퇴 대상 SocialType: {}", user.getSocialType());

        // 구글일 경우 연동 해제용 액세스 토큰 가져오기
        String socialAccessToken = null;
        if (user.getSocialType() == SocialType.GOOGLE) {
            org.springframework.security.oauth2.client.OAuth2AuthorizedClient client =
                    authorizedClientService.loadAuthorizedClient("google", user.getUserId());
            if (client != null && client.getAccessToken() != null) {
                socialAccessToken = client.getAccessToken().getTokenValue();     // 토큰
            }
        }

        // 계정 비활성화 (Soft Delete)
        user.deactivate();

        // Redis에 저장된 Refresh Token 삭제
        refreshTokenRepository.deleteById(userId);

        // 소셜 연동 해제
        if (user.getSocialType() != SocialType.NONE) {
            socialUnlinkService.unlink(user.getSocialType(), user.getSocialId(), socialAccessToken);
        }

        log.info("회원 탈퇴 완료: userId={}, socialType={}", userId, user.getSocialType());
    }

    private void handleExistingUser(String userId, String mail, SocialType socialType) {
        // 아이디는 가입 방식 상관없이 전체 중복 불가능 (Spring Security 시스템 특징상 고유해야 함)
        userRepository.findByUserId(userId).ifPresent(user -> {
            if (user.getIsActive()) throw new BusinessException(ErrorCode.DUPLICATE_USER_ID);
            userRepository.delete(user);
        });

        // 이메일 중복 확인: 현재 시도하려는 가입 방식(socialType)에 대해서만 중복 체크
        userRepository.findByMailAndSocialType(mail, socialType).ifPresent(user -> {
            if (user.getIsActive()) throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
            // 같은 이메일인데 같은 방식(NONE)으로 탈퇴한 기록이 있다면 삭제 후 재가입 허용
            userRepository.delete(user);
        });
    }

    private LoginResponse getLoginResponse(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), user.getRole());

        refreshTokenRepository.save(new RefreshToken(user.getId(), refreshToken));

        return LoginResponse.of(user, accessToken, refreshToken);
    }

    private User findByIdAndValidateActive(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessException(ErrorCode.INACTIVE_USER);
        }

        String banStatus = user.getBanStatus();
        if (!"ACTIVE".equals(banStatus)) {
            throw new BusinessException(ErrorCode.BANNED_USER);
        }

        return user;
    }

    private User findByUserIdAndValidateActive(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 탈퇴 여부 먼저 확인
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessException(ErrorCode.INACTIVE_USER);
        }

        // 밴 상태 확인
        String banStatus = user.getBanStatus();
        if (!"ACTIVE".equals(banStatus)) {
            // "BANNED"나 "PERMANENT_BANNED"인 경우 에러 발생
            throw new BusinessException(ErrorCode.BANNED_USER);
        }

        return user;
    }

    @Transactional
    public void changePassword(String userId, PasswordChangeRequest request) {
        // 유저 조회
        User user = findByIdAndValidateActive(userId);

        // 소셜 로그인 유저는 비밀번호 변경 불가
        if (user.getSocialType() != SocialType.NONE) {
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_CANNOT_USE_RECOVERY);
        }

        // 현재 비밀번호가 일치하는지 확인
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD); // "현재 비밀번호가 틀렸습니다"
        }

        // 새 비밀번호가 현재 비밀번호와 똑같은지 확인 (재사용 방지)
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_SAME_AS_OLD);
        }

        // 비밀번호 암호화 및 업데이트
        user.updatePassword(passwordEncoder.encode(request.newPassword()));
        log.info("비밀번호 변경 완료: userId={}", userId);
    }
}
