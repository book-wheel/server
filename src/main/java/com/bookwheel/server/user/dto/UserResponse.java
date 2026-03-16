package com.bookwheel.server.user.dto;

import com.bookwheel.server.user.entity.Role;
import com.bookwheel.server.user.entity.SocialType;
import com.bookwheel.server.user.entity.User;
import lombok.Builder;

@Builder
public record UserResponse(
        Role role,
        String id,
        String loginId,
        String nickname,
        String mail,
        SocialType social,
        String comment,
        String profileImageUrl
) {
    public static UserResponse from(User user, String profileImageUrl) {
        return UserResponse.builder()
                .role(user.getRole())
                .id(user.getId())
                .loginId(user.getUserId())
                .nickname(user.getNickname())
                .mail(user.getMail())
                .social(user.getSocialType())
                .comment(user.getComment())
                .profileImageUrl(profileImageUrl)
                .build();
    }
}
