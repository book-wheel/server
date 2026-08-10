package com.bookwheel.server.user.controller;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.oauth2.OAuth2LoginCodeService;
import com.bookwheel.server.common.oauth2.OAuth2Pkce;
import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.user.dto.*;
import com.bookwheel.server.user.service.EmailService;
import com.bookwheel.server.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;

import java.io.IOException;

@Tag(name = "Auth", description = "인증/인가 관련 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final EmailService emailService;
    private final OAuth2LoginCodeService oAuth2LoginCodeService;

    @Operation(summary = "일반 회원가입 (Stage 1)", description = "이메일 인증 완료 후 아이디와 비밀번호만 입력해 계정을 생성합니다. 닉네임은 추후 프로필 설정에서 입력합니다.")
    @PostMapping("/signup")
    public ApiResponse<UserResponse> signup(@Valid @RequestBody UserSignupRequest request) {
        return ApiResponse.success(userService.signup(request));
    }

    @Operation(summary = "로그인", description = "아이디와 비밀번호를 입력해 JWT 토큰을 발급받습니다.")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody UserLoginRequest request) {
        return ApiResponse.success(userService.login(request));
    }

    @Operation(summary = "이메일 인증번호 전송", description = "회원가입을 위해 이메일로 6자리 인증번호를 보냅니다.")
    @PostMapping("/emails/send")
    public ApiResponse<?> sendEmailVerification(@Valid @RequestBody EmailVerificationRequest request) {
        userService.checkEmailDuplication(request.email());
        emailService.sendVerificationCode(request.email());
        return ApiResponse.success("인증번호가 발송되었습니다.");
    }

    @Operation(summary = "이메일 인증번호 검증", description = "이메일로 받은 인증번호가 맞는지 확인합니다.")
    @PostMapping("/emails/verify")
    public ApiResponse<?> verifyEmailCode(@Valid @RequestBody EmailVerificationCodeRequest request) {
        emailService.verifyCode(request.email(), request.code());
        return ApiResponse.success("이메일 인증이 완료되었습니다.");
    }

    @Operation(
            summary = "소셜 로그인 시작",
            description = "앱이 생성한 PKCE S256 code challenge를 세션에 저장한 뒤 각 플랫폼의 로그인 페이지로 리다이렉트합니다."
    )
    @GetMapping("/authorize/{provider}")
    public void socialLogin(
            @PathVariable String provider,
            @Parameter(description = "PKCE S256 code challenge", required = true)
            @RequestParam(required = false) String codeChallenge,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        if (!OAuth2Pkce.isValidCodeChallenge(codeChallenge)) {
            throw new BusinessException(ErrorCode.INVALID_PKCE_VALUE);
        }
        request.getSession(true).setAttribute(OAuth2Pkce.SESSION_ATTRIBUTE, codeChallenge);

        // 진짜 시큐리티 소셜 로그인 주소로 리다이렉트
        String redirectUrl = "/oauth2/authorization/" + provider;
        response.sendRedirect(redirectUrl);
    }

    @Operation(
            summary = "소셜 로그인 코드 교환",
            description = "앱 딥링크로 전달된 일회용 코드와 PKCE verifier를 검증한 뒤 JWT 토큰을 발급합니다. 코드는 1분간 한 번만 사용할 수 있습니다."
    )
    @PostMapping("/oauth2/token")
    public ResponseEntity<ApiResponse<OAuth2TokenResponse>> exchangeOAuth2LoginCode(
            @Valid @RequestBody OAuth2LoginCodeExchangeRequest request
    ) {
        OAuth2TokenResponse tokenResponse = oAuth2LoginCodeService.exchange(request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(ApiResponse.success(tokenResponse));
    }

    @Operation(summary = "토큰 재발급", description = "만료된 Access Token 대신 Refresh Token을 이용해 새로운 토큰을 발급받습니다.")
    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> reissue(@Valid @RequestBody TokenReissueRequest request) {
        return ApiResponse.success(userService.reissue(request));
    }
}
