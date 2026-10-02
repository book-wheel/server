package com.bookwheel.server.common.oauth2.apple;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AppleOAuthCredentialServiceTest {

    @Mock private AppleOAuthCredentialRepository credentialRepository;
    @Mock private AppleRefreshTokenCipher tokenCipher;
    @Mock private AppleTokenRevocationClient revocationClient;

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-09-17T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );
    private AppleOAuthCredentialService service;

    @BeforeEach
    void setUp() {
        service = new AppleOAuthCredentialService(
                credentialRepository, tokenCipher, revocationClient, clock
        );
    }

    @Test
    @DisplayName("Apple refresh token은 암호화한 값만 저장한다")
    void storesOnlyEncryptedRefreshToken() {
        given(tokenCipher.encrypt("user-pk", "plain-refresh-token"))
                .willReturn(new AppleRefreshTokenCipher.EncryptionResult("v1", "encrypted-token"));
        given(credentialRepository.findByUserPKForUpdate("user-pk")).willReturn(Optional.empty());

        service.store("user-pk", "plain-refresh-token");

        ArgumentCaptor<AppleOAuthCredential> captor = ArgumentCaptor.forClass(AppleOAuthCredential.class);
        then(credentialRepository).should().save(captor.capture());
        assertThat(captor.getValue().getUserPK()).isEqualTo("user-pk");
        assertThat(captor.getValue().getEncryptedRefreshToken()).isEqualTo("encrypted-token");
        assertThat(captor.getValue().getEncryptionKeyVersion()).isEqualTo("v1");
    }

    @Test
    @DisplayName("Token storage does not cancel an existing Apple revocation request")
    void preservesRevocationRequestWhenTokenIsStoredAgain() {
        AppleOAuthCredential credential = requestedCredential();
        given(tokenCipher.encrypt("user-pk", "new-refresh-token"))
                .willReturn(new AppleRefreshTokenCipher.EncryptionResult("v2", "new-encrypted-token"));
        given(credentialRepository.findByUserPKForUpdate("user-pk"))
                .willReturn(Optional.of(credential));

        service.store("user-pk", "new-refresh-token");

        assertThat(credential.getEncryptedRefreshToken()).isEqualTo("new-encrypted-token");
        assertThat(credential.getEncryptionKeyVersion()).isEqualTo("v2");
        assertThat(credential.isRevocationRequested()).isTrue();
        assertThat(credential.getNextAttemptAt())
                .isEqualTo(LocalDateTime.of(2026, 9, 17, 9, 59));
        then(credentialRepository).should().save(credential);
    }

    @Test
    @DisplayName("탈퇴 시 Apple token 폐기를 즉시 실행 가능한 상태로 예약한다")
    void requestsRevocation() {
        AppleOAuthCredential credential = credential();
        given(credentialRepository.findByUserPKForUpdate("user-pk"))
                .willReturn(Optional.of(credential));

        service.requestRevocation("user-pk");

        assertThat(credential.isRevocationRequested()).isTrue();
        assertThat(credential.getNextAttemptAt())
                .isEqualTo(LocalDateTime.of(2026, 9, 17, 10, 0));
    }

    @Test
    @DisplayName("Apple token 폐기에 성공하면 저장된 자격증명을 삭제한다")
    void deletesCredentialAfterSuccessfulRevocation() {
        AppleOAuthCredential credential = requestedCredential();
        given(credentialRepository.findByUserPKForUpdate("user-pk"))
                .willReturn(Optional.of(credential));
        given(tokenCipher.decrypt("user-pk", "v1", "encrypted-token"))
                .willReturn("plain-refresh-token");

        assertThat(service.processOne("user-pk")).isTrue();

        then(revocationClient).should().revokeRefreshToken("plain-refresh-token");
        then(credentialRepository).should().delete(credential);
    }

    @Test
    @DisplayName("Apple token 폐기에 실패하면 원문이나 오류 본문 없이 재시도를 예약한다")
    void schedulesRetryAfterFailedRevocation() {
        AppleOAuthCredential credential = requestedCredential();
        given(credentialRepository.findByUserPKForUpdate("user-pk"))
                .willReturn(Optional.of(credential));
        given(tokenCipher.decrypt("user-pk", "v1", "encrypted-token"))
                .willReturn("plain-refresh-token");
        doThrow(new IllegalStateException("sensitive response"))
                .when(revocationClient).revokeRefreshToken("plain-refresh-token");

        assertThat(service.processOne("user-pk")).isFalse();

        assertThat(credential.getAttemptCount()).isEqualTo(1);
        assertThat(credential.getNextAttemptAt())
                .isEqualTo(LocalDateTime.of(2026, 9, 17, 10, 5));
        assertThat(credential.getLastError()).isEqualTo("IllegalStateException");
        then(credentialRepository).should(never()).delete(credential);
    }

    private AppleOAuthCredential requestedCredential() {
        AppleOAuthCredential credential = credential();
        credential.requestRevocation(LocalDateTime.of(2026, 9, 17, 9, 59));
        return credential;
    }

    private AppleOAuthCredential credential() {
        return new AppleOAuthCredential(
                "user-pk",
                "encrypted-token",
                "v1",
                LocalDateTime.of(2026, 9, 1, 0, 0)
        );
    }
}
