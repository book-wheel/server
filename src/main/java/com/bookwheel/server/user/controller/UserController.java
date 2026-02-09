package com.bookwheel.server.user.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.user.dto.*;
import com.bookwheel.server.user.service.EmailService;
import com.bookwheel.server.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final EmailService emailService;

    @PostMapping("/email/send")
    public ApiResponse<?> sendEmailVerification(@Valid @RequestBody EmailVerificationRequest request) {
        emailService.sendVerificationCode(request.email());
        return ApiResponse.success("인증번호가 발송되었습니다.");
    }

    @PostMapping("/email/verify")
    public ApiResponse<?> verifyEmailCode(@Valid @RequestBody EmailVerificationCodeRequest request) {
        emailService.verifyCode(request.email(), request.code());
        return ApiResponse.success("이메일 인증이 완료되었습니다.");
    }

    @PostMapping("/signup")
    public ApiResponse<UserResponse> signup(@Valid @RequestBody UserSignupRequest request) {
        UserResponse response = userService.signup(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/login")
    public ApiResponse<UserResponse> login(@Valid @RequestBody UserLoginRequest request) {
        UserResponse response = userService.login(request);
        return ApiResponse.success(response);
    }
}

