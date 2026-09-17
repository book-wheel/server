package com.bookwheel.server.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "회원 탈퇴 요청. 일반 회원은 비밀번호가 필수이고 소셜 회원은 요청 본문을 생략할 수 있다.")
public record UserWithdrawRequest(
        @Schema(description = "비밀번호 (일반 로그인 회원 필수)", example = "password123!")
        String password
) {}
