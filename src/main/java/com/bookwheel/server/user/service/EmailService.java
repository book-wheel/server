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

    private static final String VERIFICATION_CODE_PREFIX = "email_verification:";
    private static final int CODE_LENGTH = 6;
    private static final int EXPIRATION_MINUTES = 5;

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom random = new SecureRandom();

    public void sendVerificationCode(String email) {
        String code = generateVerificationCode();

        try {
            sendEmail(email, "BookWheel 이메일 인증",
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

        deleteVerificationCode(email);
        log.info("이메일 인증 완료: {}", email);
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

