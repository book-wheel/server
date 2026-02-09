package com.bookwheel.server.user.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.jwt.JwtTokenProvider; // 추가됨
import com.bookwheel.server.user.dto.LoginResponse;      // 추가됨
import com.bookwheel.server.user.dto.UserLoginRequest;
import com.bookwheel.server.user.dto.UserResponse;
import com.bookwheel.server.user.dto.UserSignupRequest;
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

    // 반환 타입 변경: UserResponse -> LoginResponse
    public LoginResponse login(UserLoginRequest request) {
        User user = findByUserIdAndValidateActive(request.userId());

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        // 토큰 발급
        String accessToken = jwtTokenProvider.createAccessToken(user.getUserId());

        log.info("로그인 성공: userId={}", user.getUserId());

        // 토큰과 함께 응답 반환
        return LoginResponse.of(user, accessToken);
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

    private User findByUserIdAndValidateActive(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!user.getIsActive()) {
            throw new BusinessException(ErrorCode.INACTIVE_USER);
        }
        return user;
    }
}