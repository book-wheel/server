package com.bookwheel.server.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserSignupRequest(
        @Schema(description = "사용자 아이디", example = "bookwheel123")
        @NotBlank(message = "아이디를 입력해주세요")
        @Size(min = 4, max = 20, message = "아이디는 4자 이상 20자 이하로 입력해주세요")
        String userId,

        @Schema(description = "비밀번호", example = "password1234!")
        @NotBlank(message = "비밀번호를 입력해주세요")
        @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하로 입력해주세요")
        String password,

        @Schema(description = "이메일", example = "test@example.com")
        @NotBlank(message = "이메일을 입력해주세요")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String mail
) {}