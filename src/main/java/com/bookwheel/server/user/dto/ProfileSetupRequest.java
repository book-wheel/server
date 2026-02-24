package com.bookwheel.server.user.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileSetupRequest {
    private String profileImageUrl;
    private String comment;
    private String nickname;
}