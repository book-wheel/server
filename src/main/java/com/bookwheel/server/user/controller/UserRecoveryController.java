package com.bookwheel.server.user.controller;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.oauth2.CustomOAuth2User;
import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.user.dto.IdRecoveryResponse;
import com.bookwheel.server.user.dto.PasswordResetRequest;
import com.bookwheel.server.user.dto.RecoveryCodeRequest;
import com.bookwheel.server.user.dto.RecoveryEmailRequest;
import com.bookwheel.server.user.service.UserRecoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User-Recovery", description = "계정 찾기(ID/PW) 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/recovery")
public class UserRecoveryController {

    private final UserRecoveryService userRecoveryService;

    @Operation(summary = "인증번호 발송", description = "ID/PW 찾기를 위해 메일로 인증번호를 쏩니다.")
    @PostMapping("/send-code")
    public ApiResponse<Void> sendCode(@Valid @RequestBody RecoveryEmailRequest request) {
        userRecoveryService.sendRecoveryCode(request.mail());
        return ApiResponse.success(null);
    }

    @Operation(summary = "아이디 찾기 인증", description = "인증번호 확인 후 아이디를 반환합니다.")
    @PostMapping("/verify-id")
    public ApiResponse<IdRecoveryResponse> verifyId(@Valid @RequestBody RecoveryCodeRequest request) {
        return ApiResponse.success(userRecoveryService.verifyCodeAndFindId(request));
    }

    @Operation(summary = "비밀번호 재설정 인증", description = "인증번호 확인 후 임시 토큰을 발급합니다.")
    @PostMapping("/verify-password")
    public ApiResponse<String> verifyPassword(@Valid @RequestBody RecoveryCodeRequest request) {
        return ApiResponse.success(userRecoveryService.verifyCodeForPassword(request));
    }

    @Operation(summary = "비밀번호 최종 변경", description = "임시 토큰과 새 비밀번호(형식 준수)로 변경합니다.")
    @PatchMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        userRecoveryService.resetPassword(request);
        return ApiResponse.success(null);
    }

    // 중복 코드 방지를 위한 프라이빗 메서드
    private String getUserIdFromPrincipal(Object principal) {
        if (principal instanceof CustomOAuth2User) {
            return ((CustomOAuth2User) principal).getUserId();
        } else if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        }
        throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
}