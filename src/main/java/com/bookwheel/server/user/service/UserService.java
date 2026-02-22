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
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
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

    // [TODO] 실제 운영 환경에서는 S3 같은 클라우드 스토리지로 변경 필요
    private final String uploadPath = Paths.get(System.getProperty("user.home"), "Desktop", "bookwheel", "profiles").toString();

    @Transactional
    public UserResponse signup(UserSignupRequest request) {
        if (!emailService.isVerified(request.mail())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
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

    @Transactional
    public LoginResponse setupProfile(String userId, ProfileSetupRequest request) {
        User user = findByUserIdAndValidateActive(userId);

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

        // 프로필 이미지 실제 저장 처리
        if (request.getProfileImage() != null && !request.getProfileImage().isEmpty()) {
            String storeFileName = saveImage(request.getProfileImage());
            user.updateProfileImage("/images/profiles/" + storeFileName); // 접근용 URL 경로 저장
        }

        user.updateComment(request.getComment());

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
        User user = findByUserIdAndValidateActive(userId);
        // [TODO] 추후 탈퇴하려는 회원이 가입된 모임이 있는지 확인하는 로직 추가 필요

        if (user.getSocialType() == SocialType.NONE) {
            if (request == null || request.password() == null ||
                    !passwordEncoder.matches(request.password(), user.getPassword())) {
                throw new BusinessException(ErrorCode.INVALID_PASSWORD);
            }
        }

        // [디버깅] 현재 시큐리티 세션에 기록된 진짜 이름을 확인
        String principalName = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getName();
        log.info("시큐리티가 기억하는 유저 이름(Principal Name): {}", principalName);
        log.info("우리가 DB에서 가져온 유저 ID(userId): {}", user.getUserId());

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

    /**
     * 중복 및 재가입 처리 로직 (이메일은 가입 방식이 다르면 허용함)
     */
    private void handleExistingUser(String userId, String mail, SocialType socialType) {
        // 1. 아이디는 가입 방식 상관없이 전체 중복 불가능 (Spring Security 시스템 특징상 고유해야 함)
        userRepository.findByUserId(userId).ifPresent(user -> {
            if (user.getIsActive()) throw new BusinessException(ErrorCode.DUPLICATE_USER_ID);
            userRepository.delete(user);
        });

        // 2. 이메일 중복 확인: 현재 시도하려는 가입 방식(socialType)에 대해서만 중복 체크
        userRepository.findByMailAndSocialType(mail, socialType).ifPresent(user -> {
            if (user.getIsActive()) throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
            // 같은 이메일인데 같은 방식(NONE)으로 탈퇴한 기록이 있다면 삭제 후 재가입 허용
            userRepository.delete(user);
        });
    }

    private String saveImage(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String storeFileName = UUID.randomUUID() + "_" + originalFilename;

        try {
            File folder = new File(uploadPath);
            if (!folder.exists()) {
                folder.mkdirs();
            }
            file.transferTo(new File(uploadPath + File.separator + storeFileName));
        } catch (IOException e) {
            log.error("파일 저장 실패: ", e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR);
        }
        return storeFileName;
    }

    private LoginResponse getLoginResponse(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getUserId(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId(), user.getRole());

        refreshTokenRepository.save(new RefreshToken(user.getUserId(), refreshToken));

        return LoginResponse.of(user, accessToken, refreshToken);
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