package com.bookwheel.server.user.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.user.dto.IdRecoveryResponse;
import com.bookwheel.server.user.dto.PasswordResetRequest;
import com.bookwheel.server.user.dto.RecoveryCodeRequest;
import com.bookwheel.server.user.entity.SocialType;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserRecoveryService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final StringRedisTemplate redisTemplate;

    private static final String CODE_PREFIX = "RECOVERY:CODE:";
    private static final String LIMIT_PREFIX = "SEND_LIMIT:";
    private static final int CODE_EXPIRATION_MINUTES = 5;
    private static final int LIMIT_EXPIRATION_SECONDS = 60;

    private static final String TOKEN_PREFIX = "RECOVERY:TOKEN:";
    private static final int TOKEN_EXPIRATION_MINUTES = 5;

    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public void sendRecoveryCode(String mail) {

        User user = userRepository.findByMailAndSocialTypeAndIsActiveTrue(mail, SocialType.NONE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 소셜 로그인 계정인 경우 예외 발생
        if (user.getSocialType() != SocialType.NONE) {
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_CANNOT_USE_RECOVERY);
        }

        // 발송 제한(1분) 체크
        if (Boolean.TRUE.equals(redisTemplate.hasKey(LIMIT_PREFIX + mail))) {
            throw new BusinessException(ErrorCode.TOO_MANY_EMAIL_REQUESTS);
        }

        // 6자리 인증번호 생성
        String verificationCode = generateVerificationCode();

        // Redis에 인증번호 저장 (5분 유효)
        redisTemplate.opsForValue().set(
                CODE_PREFIX + mail,
                verificationCode,
                CODE_EXPIRATION_MINUTES,
                TimeUnit.MINUTES
        );

        // 발송 제한 기록 저장 (1분 유효)
        redisTemplate.opsForValue().set(
                LIMIT_PREFIX + mail,
                "LOCKED",
                LIMIT_EXPIRATION_SECONDS,
                TimeUnit.SECONDS
        );

        // 이메일 발송
        emailService.sendVerificationMail(mail, verificationCode);
        log.info("인증번호 발송 완료: mail={}, code={}", mail, verificationCode);
    }

    private String generateVerificationCode() {
        SecureRandom random = new SecureRandom();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    @Transactional(readOnly = true)
    public IdRecoveryResponse verifyCodeAndFindId(RecoveryCodeRequest request) {
        String mail = request.mail();
        String code = request.code();

        // Redis에서 해당 이메일의 인증번호 조회
        String storedCode = redisTemplate.opsForValue().get(CODE_PREFIX + mail);

        // 만료 여부 확인
        if (storedCode == null) {
            throw new BusinessException(ErrorCode.EXPIRED_VERIFICATION_CODE);
        }

        // 일치 여부 확인
        if (!storedCode.equals(code)) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE);
        }

        // 아이디 조회 및 반환 (전체 아이디)
        User user = userRepository.findByMailAndSocialTypeAndIsActiveTrue(mail, SocialType.NONE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 인증 성공 후 보안을 위해 Redis의 인증번호 삭제
        redisTemplate.delete(CODE_PREFIX + mail);

        return IdRecoveryResponse.from(user.getUserId());
    }

     // 비밀번호 재설정을 위한 인증번호 검증
     // 성공 시 5분간 유효한 Reset-Token 발급
    @Transactional(readOnly = true)
    public String verifyCodeForPassword(RecoveryCodeRequest request) {
        String mail = request.mail();
        String code = request.code();

        User user = userRepository.findByMailAndSocialTypeAndIsActiveTrue(mail, SocialType.NONE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 소셜 로그인 계정인 경우 예외 발생
        if (user.getSocialType() != SocialType.NONE) {
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_CANNOT_USE_RECOVERY);
        }

        // Redis에서 인증번호 확인
        String storedCode = redisTemplate.opsForValue().get(CODE_PREFIX + mail);
        if (storedCode == null) {
            throw new BusinessException(ErrorCode.EXPIRED_VERIFICATION_CODE);
        }
        if (!storedCode.equals(code)) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE);
        }

        // 인증 성공 시 재설정 전용 임시 토큰 생성 (UUID)
        String resetToken = java.util.UUID.randomUUID().toString();

        // Redis에 토큰 저장 (Key: 토큰, Value: 이메일)
        redisTemplate.opsForValue().set(
                TOKEN_PREFIX + resetToken,
                mail,
                TOKEN_EXPIRATION_MINUTES,
                TimeUnit.MINUTES
        );

        // 보안을 위해 사용한 인증번호 즉시 삭제
        redisTemplate.delete(CODE_PREFIX + mail);

        return resetToken;
    }

    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        // 토큰 검증 및 이메일 조회
        String mail = redisTemplate.opsForValue().get(TOKEN_PREFIX + request.resetToken());
        if (mail == null) {
            throw new BusinessException(ErrorCode.EXPIRED_RECOVERY_TOKEN);
        }

        // 유저 조회
        User user = userRepository.findByMailAndSocialTypeAndIsActiveTrue(mail, SocialType.NONE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 기존 비밀번호와 동일한지 체크
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_SAME_AS_OLD);
        }

        // 비밀번호 암호화 및 업데이트
        user.updatePassword(passwordEncoder.encode(request.newPassword()));

        // 토큰 삭제
        redisTemplate.delete(TOKEN_PREFIX + request.resetToken());
    }
}