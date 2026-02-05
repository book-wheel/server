package com.bookwheel.user.dto;

import com.bookwheel.user.entity.SocialType;
import com.bookwheel.user.entity.User;

public record UserResponse(
    String id,
    String userId,
    String nickname,
    String mail,
    SocialType social,
    String comment,
    String profileImage
) {
    public static UserResponse from(User user) {
        return new UserResponse(
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

