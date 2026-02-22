package com.bookwheel.server.user.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class ProfileSetupRequest {
    private MultipartFile profileImage;
    private String comment;
    private String nickname;
}