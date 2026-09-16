package com.bookwheel.server.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record ProfileSetupRequest (
    @Schema(
        description = "누락 시 기존 이미지 유지, 빈 문자열 시 삭제, 전용 Presigned URL API가 발급한 "
                + "userPK 귀속 profiles-temp/ S3 key 전달 시 검증 후 교체. "
                + "하위 호환을 위해 기존 profiles/ key를 재전송해도 DB와 일치하는 경우에만 유지",
        example = "profiles-temp/550e8400-e29b-41d4-a716-446655440000/"
                + "8d59e31d-25a4-4138-9b13-ffb692478a29.png"
    )
    String profileImageKey,
    String comment,
    String nickname,
    @Schema(description = "소셜 최초 가입 시 이용약관 필수 동의", example = "true")
    Boolean termsAgreed,
    @Schema(description = "소셜 최초 가입 시 개인정보처리방침 필수 동의", example = "true")
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
) {
    public ProfileSetupRequest(String profileImageKey, String comment, String nickname) {
        this(profileImageKey, comment, nickname, null, null, null, null, null, null);
    }
}
