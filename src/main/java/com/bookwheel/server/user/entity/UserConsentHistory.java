package com.bookwheel.server.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 회원 본체가 삭제된 뒤에도 제한적으로 보관하는 동의 증빙 이력이다.
 * 회원 hard delete를 막지 않도록 User 엔티티와 외래키 관계를 두지 않는다.
 */
@Entity
@Table(
        name = "user_consent_history",
        indexes = {
                @Index(name = "idx_consent_history_user_pk", columnList = "user_pk"),
                @Index(name = "idx_consent_history_subject_hash", columnList = "subject_identifier_hash"),
                @Index(name = "idx_consent_history_retention_until", columnList = "retention_until")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consent_record_pk")
    private Long consentRecordPK;

    @Column(name = "user_pk", length = 50, nullable = false, updatable = false)
    private String userPK;

    @Column(name = "subject_identifier_hash", length = 64, nullable = false, updatable = false)
    private String subjectIdentifierHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_identifier_type", length = 30, nullable = false, updatable = false)
    private ConsentSubjectIdentifierType subjectIdentifierType;

    @Column(name = "hash_algorithm", length = 30, nullable = false, updatable = false)
    private String hashAlgorithm;

    @Column(name = "hash_key_version", length = 30, nullable = false, updatable = false)
    private String hashKeyVersion;

    @Column(name = "terms_agreed", nullable = false, updatable = false)
    private boolean termsAgreed;

    @Column(name = "terms_version", length = 50, nullable = false, updatable = false)
    private String termsVersion;

    @Column(name = "terms_document_hash", length = 64, nullable = false, updatable = false)
    private String termsDocumentHash;

    @Column(name = "privacy_agreed", nullable = false, updatable = false)
    private boolean privacyAgreed;

    @Column(name = "privacy_version", length = 50, nullable = false, updatable = false)
    private String privacyVersion;

    @Column(name = "privacy_document_hash", length = 64, nullable = false, updatable = false)
    private String privacyDocumentHash;

    @Column(name = "marketing_agreed", nullable = false, updatable = false)
    private boolean marketingAgreed;

    @Column(name = "marketing_version", length = 50, updatable = false)
    private String marketingVersion;

    @Column(name = "marketing_document_hash", length = 64, updatable = false)
    private String marketingDocumentHash;

    @Column(name = "agreed_at", nullable = false, updatable = false)
    private LocalDateTime agreedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_source", length = 30, nullable = false, updatable = false)
    private ConsentSource consentSource;

    @Column(name = "withdrawal_requested_at")
    private LocalDateTime withdrawalRequestedAt;

    @Column(name = "retention_until")
    private LocalDateTime retentionUntil;

    public UserConsentHistory(
            String userPK,
            String subjectIdentifierHash,
            ConsentSubjectIdentifierType subjectIdentifierType,
            String hashAlgorithm,
            String hashKeyVersion,
            boolean termsAgreed,
            String termsVersion,
            String termsDocumentHash,
            boolean privacyAgreed,
            String privacyVersion,
            String privacyDocumentHash,
            boolean marketingAgreed,
            String marketingVersion,
            String marketingDocumentHash,
            LocalDateTime agreedAt,
            ConsentSource consentSource
    ) {
        this.userPK = userPK;
        this.subjectIdentifierHash = subjectIdentifierHash;
        this.subjectIdentifierType = subjectIdentifierType;
        this.hashAlgorithm = hashAlgorithm;
        this.hashKeyVersion = hashKeyVersion;
        this.termsAgreed = termsAgreed;
        this.termsVersion = termsVersion;
        this.termsDocumentHash = termsDocumentHash;
        this.privacyAgreed = privacyAgreed;
        this.privacyVersion = privacyVersion;
        this.privacyDocumentHash = privacyDocumentHash;
        this.marketingAgreed = marketingAgreed;
        this.marketingVersion = marketingVersion;
        this.marketingDocumentHash = marketingDocumentHash;
        this.agreedAt = agreedAt;
        this.consentSource = consentSource;
    }
}
