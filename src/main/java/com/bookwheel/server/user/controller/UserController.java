package com.bookwheel.server.user.controller;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.oauth2.CustomOAuth2User;
import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.user.dto.*;
import com.bookwheel.server.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "Users", description = "회원 정보 관리 API")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "프로필 설정", description = "가입 후 첫 로그인 시 프로필 사진과 코멘트를 설정합니다.")
    @PatchMapping("/setup-profile")
    public ApiResponse<LoginResponse> setupProfile(
            @AuthenticationPrincipal Object principal,
            @Valid @RequestBody ProfileSetupRequest request) {

        String userId = getUserIdFromPrincipal(principal);
        LoginResponse response = userService.setupProfile(userId, request);
        return ApiResponse.success(response);
    }

    @Operation(summary = "내 정보 조회", description = "로그인한 사용자의 정보를 조회합니다. (토큰 필요)")
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMyInfo(@AuthenticationPrincipal Object principal) {
        String userId = getUserIdFromPrincipal(principal);
        UserResponse response = userService.getMyInfo(userId);
        return ApiResponse.success(response);
    }

    @Operation(summary = "로그아웃", description = "사용자를 로그아웃 처리하고 Redis의 Refresh Token을 삭제합니다.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Object principal) {
        String userId = getUserIdFromPrincipal(principal);
        userService.logout(userId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "회원 탈퇴", description = "비밀번호 확인 후 계정을 비활성화 처리합니다.")
    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(
            @AuthenticationPrincipal Object principal,
            @Valid @RequestBody(required = false) UserWithdrawRequest request) {

        String userId = getUserIdFromPrincipal(principal);
        userService.withdraw(userId, request);
        return ApiResponse.success(null);
    }

    @Operation(summary = "닉네임 중복 확인", description = "입력한 닉네임이 이미 사용 중인지 확인합니다.")
    @GetMapping("/check-nickname")
    public ApiResponse<Boolean> checkNickname(@RequestParam String nickname) {
        if (userService.isNicknameDuplicate(nickname)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        return ApiResponse.success(true);
    }



//     // 소셜 유저, 일반 유저 모두에게서 String userId 추출
//    private String getUserIdFromPrincipal(Object principal) {
//        if (principal instanceof CustomOAuth2User) {
//            return ((CustomOAuth2User) principal).getUserId();
//        } else if (principal instanceof UserDetails) {
//            return ((UserDetails) principal).getUsername();
//        }
//        throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
//    }

     //  [ID 추출기 + 로그 모니터링]
    private String getUserIdFromPrincipal(Object principal) {
        // 1. 어떤 객체가 들어왔는지 클래스 이름을 로그로 찍어봅니다.
        log.info("[ID 추출기] 현재 접근한 객체 타입: {}", principal != null ? principal.getClass().getSimpleName() : "null");

        String userId;

        if (principal instanceof CustomOAuth2User oauth2User) {
            // 소셜 로그인 유저인 경우
            userId = oauth2User.getUserId();
            log.info("=> [소셜 로그인] CustomOAuth2User에서 추출된 ID: {}", userId);
        } else if (principal instanceof UserDetails userDetails) {
            // 일반 로그인 유저인 경우
            userId = userDetails.getUsername();
            log.info("=> [일반 로그인] UserDetails에서 추출된 ID: {}", userId);
        } else {
            // 로그인이 안 되어 있거나 알 수 없는 타입인 경우 (보통 Filter에서 걸러지지만 안전장치로!)
            log.warn("=> [경고] 인증 정보가 없거나 알 수 없는 타입의 접근입니다.");
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }

        return userId;
    }

    @Operation(summary = "비밀번호 직접 변경", description = "로그인한 사용자가 현재 비밀번호를 확인한 후 새로운 비밀번호로 변경합니다.")
    @PatchMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal Object principal,
            @Valid @RequestBody PasswordChangeRequest request) {

        String userId = getUserIdFromPrincipal(principal);
        userService.changePassword(userId, request);
        return ApiResponse.success(null);
    }
}