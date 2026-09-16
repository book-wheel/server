package com.bookwheel.server.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserSignupRequest(
        @Schema(description = "사용자 로그인 아이디", example = "bookwheel123")
        @NotBlank(message = "아이디를 입력해주세요")
        @Size(min = 4, max = 20, message = "아이디는 4자 이상 20자 이하로 입력해주세요")
        @Pattern(regexp = "^[A-Za-z0-9]+$", message = "아이디는 영문과 숫자만 사용할 수 있습니다.")
        String loginId,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,20}$",
                message = "비밀번호는 8~20자이며 영문, 숫자, 특수문자를 최소 하나씩 포함해야 합니다."
        )
        String password,

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String mail,

        @Schema(description = "이용약관 필수 동의", example = "true")
        Boolean termsAgreed,

        @Schema(description = "개인정보처리방침 필수 동의", example = "true")
        Boolean privacyAgreed,

        @Schema(description = "마케팅 수신 선택 동의. 누락 시 false", example = "false")
        Boolean marketingAgreed,

        @Schema(description = "동의한 이용약관 버전", example = "2026-09-01")
        @Size(max = 50)
        String termsVersion,

        @Schema(description = "동의한 개인정보처리방침 버전", example = "2026-09-01")
        @Size(max = 50)
        String privacyVersion,

        @Schema(description = "동의한 마케팅 수신 정책 버전. 마케팅 동의 시 필수", example = "2026-09-01")
        @Size(max = 50)
        String marketingVersion
) {}
