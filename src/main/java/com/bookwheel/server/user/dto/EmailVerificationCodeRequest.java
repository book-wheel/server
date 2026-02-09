package com.bookwheel.server.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmailVerificationCodeRequest(
    @NotBlank(message = "이메일을 입력해주세요")
    @Email(message = "올바른 이메일 형식을 입력해주세요")
    @Size(max = 100, message = "이메일은 최대 100자까지 입력 가능합니다")
    String email,

    @NotBlank(message = "인증번호를 입력해주세요")
    @Pattern(regexp = "^\\d{6}$", message = "인증번호는 6자리 숫자입니다")
    @Size(max = 10, message = "인증번호는 최대 10자까지 입력 가능합니다")
    String code
) {}
