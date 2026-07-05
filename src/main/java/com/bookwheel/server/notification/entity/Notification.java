package com.bookwheel.server.notification.entity;

import com.bookwheel.server.notification.enums.NotificationCategory;
import com.bookwheel.server.notification.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@Table(
        name = "notification",
        indexes = {
                @Index(name = "idx_notification_recipient", columnList = "recipient_user_pk, created_at"),
                @Index(name = "idx_notification_recipient_unread", columnList = "recipient_user_pk, is_read")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @Column(name = "recipient_user_pk", length = 50, nullable = false)
    private String recipientUserPK;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 50, nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20, nullable = false)
    private NotificationCategory category;

    @Column(name = "title", length = 100, nullable = false)
    private String title;

    @Column(name = "body", length = 500, nullable = false)
    private String body;

    @Column(name = "deep_link", length = 255)
    private String deepLink;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    public void markRead() {
        if (Boolean.TRUE.equals(this.isRead)) {
            return;
        }
        this.isRead = true;
        this.readAt = LocalDateTime.now();
    }
}
