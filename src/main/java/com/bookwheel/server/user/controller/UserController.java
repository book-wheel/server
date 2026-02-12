package com.bookwheel.server.user.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.user.dto.UserResponse;
import com.bookwheel.server.user.dto.UserSignupRequest;
import com.bookwheel.server.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
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
}