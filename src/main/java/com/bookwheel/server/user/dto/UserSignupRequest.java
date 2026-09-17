package com.bookwheel.server.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "일반 회원가입 요청. 약관 버전은 현재 약관 버전 조회 API의 응답값을 사용한다.")
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

        @Schema(description = "이용약관 필수 동의. 반드시 true", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean termsAgreed,

        @Schema(description = "개인정보처리방침 필수 동의. 반드시 true", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean privacyAgreed,

        @Schema(description = "마케팅 수신 선택 동의. 누락 시 false", example = "false", defaultValue = "false")
        Boolean marketingAgreed,

        @Schema(description = "GET /api/v1/auth/consent-policies/current에서 받은 이용약관 버전",
                example = "2026-09-01", requiredMode = Schema.RequiredMode.REQUIRED)
        @Size(max = 50)
        String termsVersion,

        @Schema(description = "GET /api/v1/auth/consent-policies/current에서 받은 개인정보처리방침 버전",
                example = "2026-09-01", requiredMode = Schema.RequiredMode.REQUIRED)
        @Size(max = 50)
        String privacyVersion,

        @Schema(description = "동의한 마케팅 수신 정책 버전. marketingAgreed=true일 때만 필수, "
                + "false 또는 누락이면 null/누락 가능", example = "2026-09-01", nullable = true)
        @Size(max = 50)
        String marketingVersion
) {}
