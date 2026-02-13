package com.bookwheel.server.user.dto;

import com.bookwheel.server.user.entity.Role;
import com.bookwheel.server.user.entity.SocialType;
import com.bookwheel.server.user.entity.User;
import lombok.Builder;

@Builder
public record UserResponse(
    Role role,
    String id,
    String userId,
    String nickname,
    String mail,
    SocialType social,
    String comment,
    String profileImage
) {
    public static UserResponse from(User user) {
        return UserResponse.builder()
                .role(user.getRole())
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

