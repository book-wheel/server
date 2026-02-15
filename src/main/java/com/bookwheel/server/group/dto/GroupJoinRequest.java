package com.bookwheel.server.group.dto;

import jakarta.validation.constraints.NotBlank;

public record GroupJoinRequest (
    String password,

    @NotBlank(message = "가입 인사를 입력해주세요.")
    String joinMent
) {}
