package com.bookwheel.server.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserLoginRequest(
        @Schema(description = "사용자 아이디", example = "bookwheel123")
        @NotBlank(message = "아이디를 입력해주세요")
        @Size(max = 50)
        String userId,

        @Schema(description = "비밀번호", example = "password1234!")
        @NotBlank(message = "비밀번호를 입력해주세요")
        @Size(min = 8, max = 100)
        String password
) {}