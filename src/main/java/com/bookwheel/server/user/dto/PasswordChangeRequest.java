package com.bookwheel.server.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "비밀번호 변경 요청")
public record PasswordChangeRequest(
        @NotBlank(message = "현재 비밀번호는 필수입니다.")
        String oldPassword,

        @NotBlank(message = "새 비밀번호는 필수입니다.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,20}$",
                message = "비밀번호는 8~20자이며 영문, 숫자, 특수문자를 최소 하나씩 포함해야 합니다."
        )
        String newPassword
) {}