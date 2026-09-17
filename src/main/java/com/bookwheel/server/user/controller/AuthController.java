package com.bookwheel.server.user.controller;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.oauth2.OAuth2LoginCodeService;
import com.bookwheel.server.common.oauth2.OAuth2Pkce;
import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.user.dto.*;
import com.bookwheel.server.user.service.EmailService;
import com.bookwheel.server.user.service.UserService;
import com.bookwheel.server.user.service.UserConsentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    private final UserConsentService userConsentService;

    @Operation(
            summary = "현재 약관 버전 조회",
            description = "회원가입 및 소셜 최초 프로필 설정 화면을 보여줄 때 조회합니다. "
                    + "버전을 하드코딩하지 말고 응답값을 동의 요청에 그대로 담아야 합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "현재 이용약관·개인정보처리방침·마케팅 정책 버전 조회 성공")
    })
    @GetMapping("/consent-policies/current")
    public ApiResponse<CurrentConsentPolicyResponse> getCurrentConsentPolicies() {
        return ApiResponse.success(CurrentConsentPolicyResponse.from(userConsentService.getCurrentPolicies()));
    }

    @Operation(
            summary = "일반 회원가입 (Stage 1)",
            description = "이메일 인증 후 필수 동의와 현재 약관 버전을 함께 제출합니다. "
                    + "마케팅 동의는 선택이며, 응답으로 프로필 설정 전용 온보딩 토큰을 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "계정 생성 성공. 온보딩 Access Token만 발급"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "이메일 미인증/입력값 오류/필수 동의 또는 버전 누락 "
                            + "(AUTH_009, AUTH_022, AUTH_025, AUTH_026, AUTH_027 등)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "제출한 약관 버전이 현재 버전과 다름. 약관 재조회 후 재동의 필요 (AUTH_028)")
    })
    @PostMapping("/signup")
    public ApiResponse<LoginResponse> signup(@Valid @RequestBody UserSignupRequest request) {
        return ApiResponse.success(userService.signup(request));
    }

    @Operation(
            summary = "로그인",
            description = "프로필 설정 완료 회원은 일반 JWT를, 미완료 회원은 온보딩 토큰을 발급받습니다."
    )
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
            description = "일회용 코드와 PKCE verifier를 검증합니다. 최초 가입자는 "
                    + "isFirstLogin=true와 온보딩 토큰을 받고, 현재 약관 동의 후 setup-profile을 호출해야 합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "코드 교환 성공. 최초 가입자는 isFirstLogin=true, refreshToken=null"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "PKCE verifier 형식이 유효하지 않음 (AUTH_024)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "일회용 코드가 만료·사용됨 또는 PKCE 검증 실패 (AUTH_023)")
    })
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
