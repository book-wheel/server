package com.bookwheel.server.common.oauth2.apple;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "apple_oauth_credential",
        indexes = @Index(
                name = "idx_apple_oauth_credential_revocation_due",
                columnList = "revocation_requested,next_attempt_at"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppleOAuthCredential {

    @Id
    @Column(name = "user_pk", length = 50, nullable = false, updatable = false)
    private String userPK;

    @Column(name = "encrypted_refresh_token", length = 4096, nullable = false)
    private String encryptedRefreshToken;

    @Column(name = "encryption_key_version", length = 20, nullable = false)
    private String encryptionKeyVersion;

    @Column(name = "revocation_requested", nullable = false)
    private boolean revocationRequested;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    public AppleOAuthCredential(
            String userPK,
            String encryptedRefreshToken,
            String encryptionKeyVersion,
            LocalDateTime createdAt
    ) {
        this.userPK = userPK;
        this.createdAt = createdAt;
        updateToken(encryptedRefreshToken, encryptionKeyVersion, createdAt);
    }

    public void updateToken(
            String encryptedRefreshToken,
            String encryptionKeyVersion,
            LocalDateTime updatedAt
    ) {
        this.encryptedRefreshToken = encryptedRefreshToken;
        this.encryptionKeyVersion = encryptionKeyVersion;
        this.updatedAt = updatedAt;
        this.revocationRequested = false;
        this.attemptCount = 0;
        this.lastAttemptAt = null;
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    public void requestRevocation(LocalDateTime requestedAt) {
        this.revocationRequested = true;
        this.updatedAt = requestedAt;
        if (this.nextAttemptAt == null || this.nextAttemptAt.isAfter(requestedAt)) {
            this.nextAttemptAt = requestedAt;
        }
    }

    public void recordFailure(LocalDateTime attemptedAt, LocalDateTime nextAttemptAt, String error) {
        this.attemptCount++;
        this.lastAttemptAt = attemptedAt;
        this.nextAttemptAt = nextAttemptAt;
        this.updatedAt = attemptedAt;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
    }
}
