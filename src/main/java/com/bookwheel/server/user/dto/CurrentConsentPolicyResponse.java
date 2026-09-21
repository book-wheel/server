package com.bookwheel.server.user.dto;

import com.bookwheel.server.user.service.ConsentPolicyRegistry;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원가입 화면에서 노출하고 가입 요청에 그대로 담아야 할 현재 약관 버전. 클라이언트에 하드코딩하지 않는다.")
public record CurrentConsentPolicyResponse(
        @Schema(description = "현재 이용약관 버전", example = "2026-09-01",
                requiredMode = Schema.RequiredMode.REQUIRED) String termsVersion,
        @Schema(description = "현재 개인정보처리방침 버전", example = "2026-09-01",
                requiredMode = Schema.RequiredMode.REQUIRED) String privacyVersion,
        @Schema(description = "현재 마케팅 수신 정책 버전", example = "2026-09-01",
                requiredMode = Schema.RequiredMode.REQUIRED) String marketingVersion
) {
    public static CurrentConsentPolicyResponse from(ConsentPolicyRegistry.CurrentPolicies policies) {
        return new CurrentConsentPolicyResponse(
                policies.termsVersion(),
                policies.privacyVersion(),
                policies.marketingVersion()
        );
    }
}
