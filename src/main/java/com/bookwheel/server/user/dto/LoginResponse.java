package com.bookwheel.server.user.dto;

import com.bookwheel.server.user.entity.Role;
import com.bookwheel.server.user.entity.SocialType;
import com.bookwheel.server.user.entity.User;
import lombok.Builder;

@Builder
public record LoginResponse(
        String accessToken,
        String refreshToken,
        boolean isProfileSet,
        Role role,
        String id,
        String loginId,
        String nickname,
        String mail,
        SocialType social,
        String comment,
        String profileImageKey
) {
    public static LoginResponse of(User user, String accessToken, String refreshToken) {
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .isProfileSet(user.isProfileSet())
                .role(user.getRole())
                .id(user.getId())
                .loginId(user.getUserId())
                .nickname(user.getNickname())
                .mail(user.getMail())
                .social(user.getSocialType())
                .comment(user.getComment())
                .profileImageKey(user.getProfileImageKey())
                .build();
    }
}
