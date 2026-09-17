package com.bookwheel.server.user.controller;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.user.dto.*;
import com.bookwheel.server.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
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
                    + "DB와 일치할 때만 유지로 처리합니다. 소셜 최초 가입자는 필수 동의 여부와 약관 버전을 "
                    + "함께 전달해야 하며, 기존 프로필 수정에서는 동의 필드를 생략할 수 있습니다. "
                    + "최초 설정 완료 시 일반 Access Token과 Refresh Token을 발급합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "프로필 설정 완료. 일반 Access Token과 Refresh Token 발급"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "필수 동의/약관 버전 누락 또는 프로필 입력값 오류 "
                            + "(AUTH_025, AUTH_026, AUTH_027 등)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "온보딩 토큰이 유효하지 않거나 이미 사용됨 (AUTH_011)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "제출한 약관 버전이 현재 버전과 다름. 약관 재조회 후 재동의 필요 (AUTH_028)")
    })
    @PatchMapping("/setup-profile")
    public ApiResponse<LoginResponse> setupProfile(
            @AuthenticationPrincipal Object principal,
            Authentication authentication,
            @Valid @RequestBody ProfileSetupRequest request) {

        String userPK = getUserPK(principal);
        boolean onboardingRequest = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ONBOARDING".equals(authority.getAuthority()));
        LoginResponse response = onboardingRequest
                ? userService.setupProfile(userPK, request, true)
                : userService.setupProfile(userPK, request);
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

    @Operation(
            summary = "회원 탈퇴",
            description = "일반 회원은 비밀번호 확인 후, 소셜 회원은 요청 본문 없이 탈퇴할 수 있습니다. "
                    + "가입한 모임이 있으면 GROUP_023으로 거절됩니다. 성공 즉시 기존 토큰은 사용할 수 없습니다. "
                    + "탈퇴 전에 삭제하지 않은 공개 게시글·댓글·리뷰·첨부 이미지는 작성자가 익명화된 상태로 유지될 수 있습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "탈퇴 접수 성공. 토큰 즉시 폐기 및 30일 후 영구 삭제 예약"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "비밀번호 불일치 (AUTH_005), 이미 탈퇴한 회원 (AUTH_010), "
                            + "가입 모임이 남아 있음 (GROUP_023)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "인증 토큰 없음/유효하지 않음 (AUTH_015 등)")
    })
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
