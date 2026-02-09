package com.bookwheel.server.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserSignupRequest(
    @NotBlank(message = "아이디를 입력해주세요")
    @Size(min = 4, max = 20, message = "아이디는 4자 이상 20자 이하로 입력해주세요")
    String userId,

    @NotBlank(message = "비밀번호를 입력해주세요")
    @Size(min = 8, max = 20, message = "비밀번호는 8자 이상 20자 이하로 입력해주세요")
    String password,

    @NotBlank(message = "닉네임을 입력해주세요")
    @Size(min = 2, max = 10, message = "닉네임은 2자 이상 10자 이하로 입력해주세요")
    String nickname,

    @NotBlank(message = "이메일을 입력해주세요")
    @Email(message = "올바른 이메일 형식을 입력해주세요")
    String mail,

    @Size(max = 100, message = "한줄소개는 100자 이하로 입력해주세요")
    String comment
) {}
