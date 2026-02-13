package com.bookwheel.server.user.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.user.dto.*;
import com.bookwheel.server.user.service.EmailService;
import com.bookwheel.server.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "인증/인가 관련 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final EmailService emailService;

    @Operation(summary = "로그인", description = "아이디와 비밀번호를 입력해 JWT 토큰을 발급받습니다.")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody UserLoginRequest request) {
        return ApiResponse.success(userService.login(request));
    }

    @Operation(summary = "이메일 인증번호 전송", description = "회원가입을 위해 이메일로 6자리 인증번호를 보냅니다.")
    @PostMapping("/emails/send")
    public ApiResponse<?> sendEmailVerification(@Valid @RequestBody EmailVerificationRequest request) {
        emailService.sendVerificationCode(request.email());
        return ApiResponse.success("인증번호가 발송되었습니다.");
    }

    @Operation(summary = "이메일 인증번호 검증", description = "이메일로 받은 인증번호가 맞는지 확인합니다.")
    @PostMapping("/emails/verify")
    public ApiResponse<?> verifyEmailCode(@Valid @RequestBody EmailVerificationCodeRequest request) {
        emailService.verifyCode(request.email(), request.code());
        return ApiResponse.success("이메일 인증이 완료되었습니다.");
    }
}