package com.bookwheel.server.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserLoginRequest(
    @NotBlank(message = "아이디를 입력해주세요") @Size(max = 50) String userId,

    @NotBlank(message = "비밀번호를 입력해주세요") @Size(min = 8, max = 100) String password
) {}
