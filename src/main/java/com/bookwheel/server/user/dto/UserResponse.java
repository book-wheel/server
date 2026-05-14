package com.bookwheel.server.user.dto;

import com.bookwheel.server.user.entity.SocialType;
import com.bookwheel.server.user.entity.User;
import lombok.Builder;

@Builder
public record UserResponse(
        String userPK,
        String loginId,
        String nickname,
        String mail,
        SocialType social,
        String comment,
        String profileImageKey
) {
    public static UserResponse from(User user, String profileImageKey) {
        return UserResponse.builder()
                .userPK(user.getId())
                .loginId(user.getLoginId())
                .nickname(user.getNickname())
                .mail(user.getMail())
                .social(user.getSocialType())
                .comment(user.getComment())
                .profileImageKey(profileImageKey)
                .build();
    }
}
