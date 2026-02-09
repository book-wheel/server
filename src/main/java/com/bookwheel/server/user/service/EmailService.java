package com.bookwheel.server.user.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String VERIFICATION_CODE_PREFIX = "email_code:";
    private static final String VERIFIED_STATUS_PREFIX = "email_verified:"; // 인증 완료 상태 저장용
    private static final int CODE_LENGTH = 6;
    private static final int EXPIRATION_MINUTES = 5;
    private static final int VERIFIED_EXPIRATION_MINUTES = 30; // 인증 완료 상태 유지 시간 - 30분

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom random = new SecureRandom();

    public void sendVerificationCode(String email) {
        String code = generateVerificationCode();
        try {
            sendEmail(email, "[책바퀴] 이메일 인증번호 안내",
                    String.format("인증번호: %s\n5분 내에 입력해주세요.", code));
            saveVerificationCode(email, code);
            log.info("인증번호 발송 완료: {}", email);
        } catch (Exception e) {
            log.error("이메일 전송 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    public void verifyCode(String email, String code) {
        String storedCode = getStoredVerificationCode(email);

        if (storedCode == null) {
            throw new BusinessException(ErrorCode.EXPIRED_VERIFICATION_CODE);
        }
        if (!storedCode.equals(code)) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE);
        }

        // 인증 성공 로직
        deleteVerificationCode(email);
        saveVerifiedStatus(email);     // "이 이메일은 인증됨" 상태 저장
        log.info("이메일 인증 완료: {}", email);
    }

    // UserService에서 호출할 메서드
    public boolean isVerified(String email) {
        String key = VERIFIED_STATUS_PREFIX + email;
        return Boolean.TRUE.toString().equals(redisTemplate.opsForValue().get(key));
    }

    private void saveVerifiedStatus(String email) {
        String key = VERIFIED_STATUS_PREFIX + email;
        redisTemplate.opsForValue().set(key, "true", VERIFIED_EXPIRATION_MINUTES, TimeUnit.MINUTES);
    }

    private String generateVerificationCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    private void sendEmail(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }

    private void saveVerificationCode(String email, String code) {
        String key = VERIFICATION_CODE_PREFIX + email;
        redisTemplate.opsForValue().set(key, code, EXPIRATION_MINUTES, TimeUnit.MINUTES);
    }

    private String getStoredVerificationCode(String email) {
        String key = VERIFICATION_CODE_PREFIX + email;
        return redisTemplate.opsForValue().get(key);
    }

    private void deleteVerificationCode(String email) {
        String key = VERIFICATION_CODE_PREFIX + email;
        redisTemplate.delete(key);
    }
}