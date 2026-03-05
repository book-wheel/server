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

    @Operation(summary = "내 정보 조회", description = "로그인한 사용자의 정보를 조회합니다. (소셜 유저도 가능!)")
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

     // 소셜 유저인지 일반 유저인지 구분해서 userId를 추출하는 메서드
     private String getUserIdFromPrincipal(Object principal) {
         if (principal instanceof CustomOAuth2User oauth2User) {
             return oauth2User.getUserId();
         } else if (principal instanceof UserDetails userDetails) {
             return userDetails.getUsername();
         }
         throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
     }

     // 소셜 유저 검증
    private void validateNonSocialUser(String userId) {
        if (userId.startsWith("GOOGLE_") || userId.startsWith("KAKAO_")) {
            log.warn("=> [경고] 소셜 유저가 금지된 기능(비밀번호 변경 등)에 접근함. ID: {}", userId);
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_CANNOT_USE_RECOVERY);
        }
    }

    @Operation(summary = "비밀번호 직접 변경", description = "로그인한 사용자가 현재 비밀번호를 확인한 후 새로운 비밀번호로 변경합니다.")
    @PatchMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal Object principal,
            @Valid @RequestBody PasswordChangeRequest request) {

        String userId = getUserIdFromPrincipal(principal);

        // 소셜 유저 검증
        validateNonSocialUser(userId);

        userService.changePassword(userId, request);
        return ApiResponse.success(null);
    }
}