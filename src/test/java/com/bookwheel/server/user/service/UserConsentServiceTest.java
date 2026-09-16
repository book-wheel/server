package com.bookwheel.server.user.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.user.entity.ConsentSource;
import com.bookwheel.server.user.config.ConsentIdentityHmacProperties;
import com.bookwheel.server.user.entity.UserConsentHistory;
import com.bookwheel.server.user.repository.UserConsentHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class UserConsentServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 17, 10, 0);

    private UserConsentHistoryRepository repository;
    private UserConsentService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserConsentHistoryRepository.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-09-17T01:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        service = new UserConsentService(
                repository,
                testHasher(),
                new ConsentPolicyRegistry(
                        "terms-2026-09", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "privacy-2026-09", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                        "marketing-2026-09", "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
                ),
                clock
        );
        given(repository.save(org.mockito.ArgumentMatchers.any(UserConsentHistory.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    private ConsentIdentityHasher testHasher() {
        ConsentIdentityHmacProperties properties = new ConsentIdentityHmacProperties();
        properties.setActiveKeyVersion("v1");
        properties.setKeys(java.util.Map.of(
                "v1", "test-consent-identity-secret-at-least-32-chars"
        ));
        return new ConsentIdentityHasher(properties);
    }

    @Test
    @DisplayName("필수 동의와 서버 시각, 동의 경로를 이력으로 저장한다")
    void recordRequiredConsentsStoresEvidence() {
        service.recordRequiredConsents(
                "user-pk",
                "User@Example.COM ",
                true,
                "terms-2026-09",
                true,
                "privacy-2026-09",
                false,
                null,
                ConsentSource.LOCAL_SIGNUP
        );

        ArgumentCaptor<UserConsentHistory> captor = ArgumentCaptor.forClass(UserConsentHistory.class);
        then(repository).should().save(captor.capture());
        UserConsentHistory history = captor.getValue();
        assertThat(history.getUserPK()).isEqualTo("user-pk");
        assertThat(history.getSubjectIdentifierHash()).hasSize(64);
        assertThat(history.getSubjectIdentifierType())
                .isEqualTo(com.bookwheel.server.user.entity.ConsentSubjectIdentifierType.EMAIL);
        assertThat(history.getHashAlgorithm()).isEqualTo(ConsentIdentityHasher.HASH_ALGORITHM);
        assertThat(history.getHashKeyVersion()).isEqualTo("v1");
        assertThat(history.getTermsDocumentHash()).hasSize(64);
        assertThat(history.getPrivacyDocumentHash()).hasSize(64);
        assertThat(history.isTermsAgreed()).isTrue();
        assertThat(history.isPrivacyAgreed()).isTrue();
        assertThat(history.isMarketingAgreed()).isFalse();
        assertThat(history.getAgreedAt()).isEqualTo(NOW);
        assertThat(history.getConsentSource()).isEqualTo(ConsentSource.LOCAL_SIGNUP);
    }

    @Test
    @DisplayName("필수 동의가 없으면 동의 이력을 저장하지 않는다")
    void recordRequiredConsentsRejectsMissingRequiredConsent() {
        assertThatThrownBy(() -> service.recordRequiredConsents(
                "user-pk", "user@example.com", false, "terms", true, "privacy", false, null,
                ConsentSource.LOCAL_SIGNUP
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.REQUIRED_CONSENT_MISSING);

        then(repository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("마케팅 수신에 동의하면 마케팅 정책 버전이 필요하다")
    void recordRequiredConsentsRequiresMarketingVersionWhenAgreed() {
        assertThatThrownBy(() -> service.recordRequiredConsents(
                "user-pk", "user@example.com", true, "terms", true, "privacy", true, null,
                ConsentSource.LOCAL_SIGNUP
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.MARKETING_CONSENT_VERSION_REQUIRED);
    }

    @Test
    @DisplayName("클라이언트 약관 버전이 서버 현재 버전과 다르면 가입을 거절한다")
    void recordRequiredConsentsRejectsStalePolicyVersion() {
        assertThatThrownBy(() -> service.recordRequiredConsents(
                "user-pk", "user@example.com",
                true, "old-terms",
                true, "privacy-2026-09",
                false, null,
                ConsentSource.LOCAL_SIGNUP
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CONSENT_POLICY_VERSION_MISMATCH);
    }

    @Test
    @DisplayName("탈퇴 시 모든 동의 이력의 보관 만료일을 3년 후로 지정한다")
    void scheduleRetentionUsesThreeYearRetention() {
        service.scheduleRetention("user-pk", NOW);

        then(repository).should().scheduleRetentionByUserPK(
                "user-pk",
                NOW,
                NOW.plusYears(3)
        );
    }

    @Test
    @DisplayName("분쟁 당사자 이메일을 HMAC으로 변환해 동의 증빙을 조회한다")
    void findEvidenceBySubjectEmailUsesProtectedIdentifier() {
        given(repository.findAllBySubjectIdentifierHashInOrderByAgreedAtAsc(
                org.mockito.ArgumentMatchers.anyCollection()
        )).willReturn(List.of());

        service.findEvidenceBySubjectEmail(" User@Example.com ");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<String>> hashCaptor = ArgumentCaptor.forClass(java.util.Collection.class);
        then(repository).should().findAllBySubjectIdentifierHashInOrderByAgreedAtAsc(hashCaptor.capture());
        assertThat(hashCaptor.getValue()).allSatisfy(hash -> assertThat(hash).hasSize(64));
    }
}
