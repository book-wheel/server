package com.bookwheel.server.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Getter
@Table(
        name = "expo_push_receipt",
        indexes = @Index(name = "idx_expo_push_receipt_created_at", columnList = "created_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ExpoPushReceipt {

    @Id
    @Column(name = "receipt_id", length = 100)
    private String receiptId;

    @Column(name = "expo_push_token", length = 255, nullable = false)
    private String expoPushToken;

    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static ExpoPushReceipt pending(
            String receiptId,
            String expoPushToken,
            Long notificationId
    ) {
        return new ExpoPushReceipt(receiptId, expoPushToken, notificationId, null);
    }
}
