package com.bookwheel.server.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "회원 탈퇴 요청 DTO")
public record UserWithdrawRequest(
        @Schema(description = "비밀번호 (일반 로그인 회원 필수)", example = "password123!")
        String password
) {}