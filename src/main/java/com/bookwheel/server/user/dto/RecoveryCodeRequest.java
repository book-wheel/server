package com.bookwheel.server.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecoveryCodeRequest(
        @NotBlank(message = "이메일은 필수 입력값입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String mail,

        @NotBlank(message = "인증번호는 필수 입력값입니다.")
        @Size(min = 6, max = 6, message = "인증번호는 6자리여야 합니다.")
        String code
) {}