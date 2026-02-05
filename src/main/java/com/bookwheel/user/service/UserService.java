package com.bookwheel.user.service;

import com.bookwheel.common.exception.BusinessException;
import com.bookwheel.common.exception.ErrorCode;
import com.bookwheel.user.dto.UserLoginRequest;
import com.bookwheel.user.dto.UserResponse;
import com.bookwheel.user.dto.UserSignupRequest;
import com.bookwheel.user.entity.User;
import com.bookwheel.user.repository.UserRepository;
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

    @Transactional
    public UserResponse signup(UserSignupRequest request) {
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

    public UserResponse login(UserLoginRequest request) {
        User user = findByUserIdAndValidateActive(request.userId());

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }

        log.info("로그인 성공: userId={}", user.getUserId());
        return UserResponse.from(user);
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

