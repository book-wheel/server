package com.bookwheel.server.user.dto;

import com.bookwheel.server.user.service.ConsentPolicyRegistry;
import io.swagger.v3.oas.annotations.media.Schema;

public record CurrentConsentPolicyResponse(
        @Schema(example = "2026-09-01") String termsVersion,
        @Schema(example = "2026-09-01") String privacyVersion,
        @Schema(example = "2026-09-01") String marketingVersion
) {
    public static CurrentConsentPolicyResponse from(ConsentPolicyRegistry.CurrentPolicies policies) {
        return new CurrentConsentPolicyResponse(
                policies.termsVersion(),
                policies.privacyVersion(),
                policies.marketingVersion()
        );
    }
}
