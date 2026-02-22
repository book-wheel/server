package com.bookwheel.server.user.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.user.dto.*;
import com.bookwheel.server.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Users", description = "회원 정보 관리 API")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "회원가입", description = "이메일 인증을 완료한 후, 회원 정보를 입력해 가입합니다.")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> signup(@Valid @RequestBody UserSignupRequest request) {
        return ApiResponse.success(userService.signup(request));
    }

    // 내 정보 조회 API
    @Operation(summary = "내 정보 조회", description = "로그인한 사용자의 정보를 조회합니다. (토큰 필요)")
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMyInfo(@AuthenticationPrincipal UserDetails userDetails) {

        // 토큰에서 꺼낸 ID
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
            @RequestBody(required = false) @Valid UserWithdrawRequest request) { // 소셜 로그인을 위해 required = false 설정
        userService.withdraw(userDetails.getUsername(), request);
        return ApiResponse.success(null);
    }
}