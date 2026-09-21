package com.bookwheel.server.user.dto;

import com.bookwheel.server.user.entity.SocialType;
import com.bookwheel.server.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "로그인 또는 프로필 설정 결과")
public record LoginResponse(
        @Schema(description = "isProfileSet=false면 프로필 설정 전용 온보딩 토큰, true면 일반 Access Token",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String accessToken,
        @Schema(description = "온보딩 단계에서는 null, 프로필 설정 완료 후에만 발급", nullable = true)
        String refreshToken,
        @Schema(description = "프로필 설정 완료 여부. false이면 일반 서비스 API 이용 불가", example = "false")
        boolean isProfileSet,
        @Schema(description = "회원 PK", example = "550e8400-e29b-41d4-a716-446655440000")
        String userPK,
        @Schema(description = "로그인 아이디")
        String loginId,
        @Schema(description = "닉네임")
        String nickname,
        @Schema(description = "이메일")
        String mail,
        @Schema(description = "가입 경로 (NONE, GOOGLE, KAKAO 등)")
        SocialType social,
        @Schema(description = "프로필 코멘트", nullable = true)
        String comment,
        @Schema(description = "프로필 이미지 S3 objectKey", nullable = true)
        String profileImageKey
) {
    public static LoginResponse of(User user, String accessToken, String refreshToken) {
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .isProfileSet(user.getIsProfileSet())
                .userPK(user.getId())
                .loginId(user.getLoginId())
                .nickname(user.getNickname())
                .mail(user.getMail())
                .social(user.getSocialType())
                .comment(user.getComment())
                .profileImageKey(user.getProfileImageKey())
                .build();
    }
}
