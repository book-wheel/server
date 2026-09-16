package com.bookwheel.server.user.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.user.entity.ConsentSource;
import com.bookwheel.server.user.entity.ConsentSubjectIdentifierType;
import com.bookwheel.server.user.entity.UserConsentHistory;
import com.bookwheel.server.user.repository.UserConsentHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserConsentService {

    private static final int CONSENT_RETENTION_YEARS = 3;

    private final UserConsentHistoryRepository consentHistoryRepository;
    private final ConsentIdentityHasher consentIdentityHasher;
    private final ConsentPolicyRegistry consentPolicyRegistry;
    private final Clock clock;

    @Transactional
    public UserConsentHistory recordRequiredConsents(
            String userPK,
            String subjectEmail,
            Boolean termsAgreed,
            String termsVersion,
            Boolean privacyAgreed,
            String privacyVersion,
            Boolean marketingAgreed,
            String marketingVersion,
            ConsentSource consentSource
    ) {
        validateRequiredConsents(
                termsAgreed,
                termsVersion,
                privacyAgreed,
                privacyVersion,
                marketingAgreed,
                marketingVersion
        );
        boolean marketingConsent = Boolean.TRUE.equals(marketingAgreed);
        ConsentPolicyRegistry.ResolvedPolicies policies = consentPolicyRegistry.resolve(
                termsVersion,
                privacyVersion,
                marketingConsent,
                marketingVersion
        );

        ConsentIdentityHasher.HashResult subjectHash = consentIdentityHasher.hashEmail(subjectEmail);
        return consentHistoryRepository.save(new UserConsentHistory(
                userPK,
                subjectHash.hash(),
                ConsentSubjectIdentifierType.EMAIL,
                ConsentIdentityHasher.HASH_ALGORITHM,
                subjectHash.keyVersion(),
                true,
                policies.terms().version(),
                policies.terms().documentHash(),
                true,
                policies.privacy().version(),
                policies.privacy().documentHash(),
                marketingConsent,
                policies.marketing() == null ? null : policies.marketing().version(),
                policies.marketing() == null ? null : policies.marketing().documentHash(),
                LocalDateTime.now(clock),
                consentSource
        ));
    }

    @Transactional
    public void scheduleRetention(String userPK, LocalDateTime withdrawalRequestedAt) {
        consentHistoryRepository.scheduleRetentionByUserPK(
                userPK,
                withdrawalRequestedAt,
                withdrawalRequestedAt.plusYears(CONSENT_RETENTION_YEARS)
        );
    }

    @Transactional
    public long deleteExpiredConsents() {
        return consentHistoryRepository.deleteByRetentionUntilLessThanEqual(LocalDateTime.now(clock));
    }

    /**
     * 분쟁 당사자가 제시한 이메일을 원본 저장 없이 동일한 HMAC으로 변환해 동의 증빙을 찾는다.
     */
    @Transactional(readOnly = true)
    public List<UserConsentHistory> findEvidenceBySubjectEmail(String email) {
        return consentHistoryRepository.findAllBySubjectIdentifierHashInOrderByAgreedAtAsc(
                consentIdentityHasher.hashEmailWithAllKeys(email).stream()
                        .map(ConsentIdentityHasher.HashResult::hash)
                        .toList()
        );
    }

    public ConsentPolicyRegistry.CurrentPolicies getCurrentPolicies() {
        return consentPolicyRegistry.current();
    }

    private void validateRequiredConsents(
            Boolean termsAgreed,
            String termsVersion,
            Boolean privacyAgreed,
            String privacyVersion,
            Boolean marketingAgreed,
            String marketingVersion
    ) {
        if (!Boolean.TRUE.equals(termsAgreed) || !Boolean.TRUE.equals(privacyAgreed)) {
            throw new BusinessException(ErrorCode.REQUIRED_CONSENT_MISSING);
        }
        if (!StringUtils.hasText(termsVersion) || !StringUtils.hasText(privacyVersion)) {
            throw new BusinessException(ErrorCode.CONSENT_VERSION_REQUIRED);
        }
        if (Boolean.TRUE.equals(marketingAgreed) && !StringUtils.hasText(marketingVersion)) {
            throw new BusinessException(ErrorCode.MARKETING_CONSENT_VERSION_REQUIRED);
        }
    }
}
