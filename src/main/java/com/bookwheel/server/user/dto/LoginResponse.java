package com.bookwheel.server.user.dto;

import com.bookwheel.server.user.entity.SocialType;
import com.bookwheel.server.user.entity.User;

public record LoginResponse(
        String accessToken,
        String id,
        String userId,
        String nickname,
        String mail,
        SocialType social,
        String comment,
        String profileImage
) {
    public static LoginResponse of(User user, String accessToken) {
        return new LoginResponse(
                accessToken,
                user.getId(),
                user.getUserId(),
                user.getNickname(),
                user.getMail(),
                user.getSocial(),
                user.getComment(),
                user.getProfileImage()
        );
    }
}