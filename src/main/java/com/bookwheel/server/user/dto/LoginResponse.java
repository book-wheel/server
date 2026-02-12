package com.bookwheel.server.user.dto;

import com.bookwheel.server.user.entity.SocialType;
import com.bookwheel.server.user.entity.User;
import lombok.Builder;

@Builder
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String id,
        String userId,
        String nickname,
        String mail,
        SocialType social,
        String comment,
        String profileImage
) {
    public static LoginResponse of(User user, String accessToken, String refreshToken) {
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .id(user.getId())
                .userId(user.getUserId())
                .nickname(user.getNickname())
                .mail(user.getMail())
                .social(user.getSocial())
                .comment(user.getComment())
                .profileImage(user.getProfileImage())
                .build();
    }
}