package com.bookwheel.server.user.controller;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.oauth2.CustomOAuth2User;
import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.user.dto.*;
import com.bookwheel.server.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Users", description = "회원 정보 관리 API")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "프로필 설정", description = "가입 후 첫 로그인 시 프로필 사진과 코멘트를 설정합니다.")
    @PatchMapping("/setup-profile")
    public ApiResponse<LoginResponse> setupProfile(
            @AuthenticationPrincipal Object principal, // 일반/소셜 유저 공통 처리를 위해 Object로 받음
            @ModelAttribute ProfileSetupRequest request) {

        String userId;
        if (principal instanceof CustomOAuth2User) {
            userId = ((CustomOAuth2User) principal).getUserId();
        } else if (principal instanceof UserDetails) {
            userId = ((UserDetails) principal).getUsername();
        } else {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
        }

        LoginResponse response = userService.setupProfile(userId, request);
        return ApiResponse.success(response);
    }

    @Operation(summary = "닉네임 중복 확인", description = "입력한 닉네임이 이미 사용 중인지 확인합니다.")
    @GetMapping("/check-nickname")
    public ApiResponse<Boolean> checkNickname(@RequestParam String nickname) {
        // userService의 메서드를 사용하도록 수정
        boolean isDuplicate = userService.isNicknameDuplicate(nickname);
        if (isDuplicate) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        return ApiResponse.success(true); // 중복이 아니면 true 반환
    }

    // 내 정보 조회 API
    @Operation(summary = "내 정보 조회", description = "로그인한 사용자의 정보를 조회합니다. (토큰 필요)")
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMyInfo(@AuthenticationPrincipal UserDetails userDetails) {
        String userId = userDetails.getUsername();
        UserResponse response = userService.getMyInfo(userId);
        return ApiResponse.success(response);
    }

    @Operation(summary = "토큰 재발급", description = "Refresh Token을 이용해 새로운 Access Token을 발급받습니다.")
    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> reissue(@RequestBody @Valid TokenReissueRequest request) {
        return ApiResponse.success(userService.reissue(request));
    }

    @Operation(summary = "로그아웃", description = "사용자를 로그아웃 처리하고 Redis의 Refresh Token을 삭제합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공")
    })
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal UserDetails userDetails) {
        userService.logout(userDetails.getUsername());
        return ApiResponse.success(null);
    }

    @Operation(summary = "회원 탈퇴", description = "비밀번호를 확인한 후 계정을 비활성화 처리하고 강제 로그아웃합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회원 탈퇴 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "비밀번호 불일치 (INVALID_PASSWORD)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않거나 이미 탈퇴한 사용자 (USER_NOT_FOUND, INACTIVE_USER)")
    })
    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody(required = false) @Valid UserWithdrawRequest request) {
        userService.withdraw(userDetails.getUsername(), request);
        return ApiResponse.success(null);
    }
}