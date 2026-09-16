package com.bookwheel.server.user.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Component
public class ConsentPolicyRegistry {

    private final Policy terms;
    private final Policy privacy;
    private final Policy marketing;

    public ConsentPolicyRegistry(
            @Value("${user.consent.policy.terms.version}") String termsVersion,
            @Value("${user.consent.policy.terms.document-hash}") String termsDocumentHash,
            @Value("${user.consent.policy.privacy.version}") String privacyVersion,
            @Value("${user.consent.policy.privacy.document-hash}") String privacyDocumentHash,
            @Value("${user.consent.policy.marketing.version}") String marketingVersion,
            @Value("${user.consent.policy.marketing.document-hash}") String marketingDocumentHash
    ) {
        this.terms = new Policy(termsVersion, termsDocumentHash);
        this.privacy = new Policy(privacyVersion, privacyDocumentHash);
        this.marketing = new Policy(marketingVersion, marketingDocumentHash);
    }

    public ResolvedPolicies resolve(
            String requestedTermsVersion,
            String requestedPrivacyVersion,
            boolean marketingAgreed,
            String requestedMarketingVersion
    ) {
        requireCurrentVersion(requestedTermsVersion, terms);
        requireCurrentVersion(requestedPrivacyVersion, privacy);
        if (marketingAgreed) {
            requireCurrentVersion(requestedMarketingVersion, marketing);
        }
        return new ResolvedPolicies(terms, privacy, marketingAgreed ? marketing : null);
    }

    public CurrentPolicies current() {
        return new CurrentPolicies(terms.version(), privacy.version(), marketing.version());
    }

    private void requireCurrentVersion(String requestedVersion, Policy currentPolicy) {
        if (!currentPolicy.version().equals(requestedVersion == null ? null : requestedVersion.strip())) {
            throw new BusinessException(ErrorCode.CONSENT_POLICY_VERSION_MISMATCH);
        }
    }

    public record Policy(String version, String documentHash) {
        public Policy {
            if (!StringUtils.hasText(version)) {
                throw new IllegalArgumentException("약관 버전은 필수입니다.");
            }
            String normalizedHash = documentHash == null
                    ? null
                    : documentHash.strip().toLowerCase(Locale.ROOT);
            if (normalizedHash == null || !normalizedHash.matches("^[0-9a-f]{64}$")) {
                throw new IllegalArgumentException("약관 원문 SHA-256은 64자리 16진수여야 합니다.");
            }
            version = version.strip();
            documentHash = normalizedHash;
        }
    }

    public record ResolvedPolicies(Policy terms, Policy privacy, Policy marketing) {
    }

    public record CurrentPolicies(String termsVersion, String privacyVersion, String marketingVersion) {
    }
}
