package com.bookwheel.server.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "s3_deletion_task",
        indexes = {
                @Index(name = "uk_s3_deletion_task_object_hash", columnList = "object_key_hash", unique = true),
                @Index(name = "idx_s3_deletion_task_next_attempt", columnList = "next_attempt_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class S3DeletionTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_pk")
    private Long taskPK;

    @Column(name = "user_pk", length = 50, nullable = false, updatable = false)
    private String userPK;

    @Column(name = "object_key", length = 1024, nullable = false, updatable = false)
    private String objectKey;

    @Column(name = "object_key_hash", length = 64, nullable = false, updatable = false)
    private String objectKeyHash;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    public S3DeletionTask(
            String userPK,
            String objectKey,
            String objectKeyHash,
            LocalDateTime createdAt
    ) {
        this.userPK = userPK;
        this.objectKey = objectKey;
        this.objectKeyHash = objectKeyHash;
        this.createdAt = createdAt;
        this.nextAttemptAt = createdAt;
    }

    public void recordFailure(LocalDateTime attemptedAt, LocalDateTime nextAttemptAt, String error) {
        this.attemptCount++;
        this.lastAttemptAt = attemptedAt;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
    }
}
