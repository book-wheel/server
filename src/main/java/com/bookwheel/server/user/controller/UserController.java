package com.bookwheel.server.user.controller;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.user.dto.*;
import com.bookwheel.server.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.bookwheel.server.common.util.SecurityUtil.getUserPK;

@Tag(name = "Users", description = "회원 정보 관리 API")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "프로필 이미지 Presigned URL 발급",
            description = "최대 5MB 프로필 이미지의 임시 업로드 URL과 userPK에 귀속된 objectKey를 발급합니다. "
                    + "업로드 후 objectKey를 setupProfile에 전달하세요."
    )
    @PostMapping("/profile-image/presigned-url")
    public ApiResponse<ProfileImagePresignedUrlResponse> createProfileImagePresignedUrl(
            @AuthenticationPrincipal Object principal,
            @Valid @RequestBody ProfileImagePresignedUrlRequest request
    ) {
        String userPK = getUserPK(principal);
        return ApiResponse.success(userService.createProfileImagePresignedUrl(userPK, request));
    }

    @Operation(
            summary = "프로필 설정",
            description = "프로필 사진과 코멘트를 설정합니다. profileImageKey는 누락 시 기존 이미지를 유지하고, "
                    + "빈 문자열이면 삭제하며, 전용 Presigned URL API가 발급한 profiles-temp/ key이면 "
                    + "검증 후 최종 이미지로 교체합니다. 기존 profiles/ key 재전송은 "
                    + "DB와 일치할 때만 유지로 처리합니다."
    )
    @PatchMapping("/setup-profile")
    public ApiResponse<LoginResponse> setupProfile(
            @AuthenticationPrincipal Object principal,
            @Valid @RequestBody ProfileSetupRequest request) {

        String userPK = getUserPK(principal);
        LoginResponse response = userService.setupProfile(userPK, request);
        return ApiResponse.success(response);
    }

    @Operation(summary = "내 정보 조회", description = "로그인한 사용자의 정보를 조회합니다. (소셜 유저도 가능!)")
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMyInfo(@AuthenticationPrincipal Object principal) {
        String userPK = getUserPK(principal);
        UserResponse response = userService.getMyInfo(userPK);
        return ApiResponse.success(response);
    }

    @Operation(
            summary = "로그아웃",
            description = "사용자를 로그아웃 처리합니다. Redis의 Refresh Token과 현재 계정에 귀속된 "
                    + "Expo Push Token을 함께 해제하여 로그아웃 후 해당 기기로 알림이 전달되지 않게 합니다."
    )
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Object principal) {
        String userPK = getUserPK(principal);
        userService.logout(userPK);
        return ApiResponse.success(null);
    }

    @Operation(summary = "회원 탈퇴", description = "비밀번호 확인 후 계정을 비활성화 처리합니다.")
    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(
            @AuthenticationPrincipal Object principal,
            @Valid @RequestBody(required = false) UserWithdrawRequest request) {
        String userPK = getUserPK(principal);
        userService.withdraw(userPK, request);
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

    @Operation(summary = "비밀번호 직접 변경", description = "로그인한 사용자가 현재 비밀번호를 확인한 후 새로운 비밀번호로 변경합니다.")
    @PatchMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal Object principal,
            @Valid @RequestBody PasswordChangeRequest request) {

        String userPK = getUserPK(principal);
        userService.changePassword(userPK, request);
        return ApiResponse.success(null);
    }
}
