package com.bookwheel.server.notification.entity;

import com.bookwheel.server.notification.enums.NotificationCategory;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@Table(
        name = "notification_preference",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_pref_user", columnNames = "user_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "preference_id")
    private Long id;

    @Column(name = "user_id", length = 50, nullable = false)
    private String userId;

    @Builder.Default
    @Column(name = "group_enabled", nullable = false)
    private Boolean groupEnabled = true;

    @Builder.Default
    @Column(name = "round_enabled", nullable = false)
    private Boolean roundEnabled = true;

    @Builder.Default
    @Column(name = "community_enabled", nullable = false)
    private Boolean communityEnabled = true;

    @Builder.Default
    @Column(name = "report_enabled", nullable = false)
    private Boolean reportEnabled = true;

    @Builder.Default
    @Column(name = "account_enabled", nullable = false)
    private Boolean accountEnabled = true;

    @Builder.Default
    @Column(name = "push_enabled", nullable = false)
    private Boolean pushEnabled = true;

    @Column(name = "fcm_token", length = 255)
    private String fcmToken;

    public static NotificationPreference defaultsFor(String userId) {
        return NotificationPreference.builder()
                .userId(userId)
                .build();
    }

    public boolean allows(NotificationCategory category) {
        return switch (category) {
            case GROUP -> Boolean.TRUE.equals(groupEnabled);
            case ROUND -> Boolean.TRUE.equals(roundEnabled);
            case COMMUNITY -> Boolean.TRUE.equals(communityEnabled);
            case REPORT -> Boolean.TRUE.equals(reportEnabled);
            // 계정 카테고리는 보안성 알림이라 사용자가 끄더라도 강제 발송
            case ACCOUNT -> true;
        };
    }

    public void updateCategoryFlags(
            Boolean groupEnabled,
            Boolean roundEnabled,
            Boolean communityEnabled,
            Boolean reportEnabled,
            Boolean pushEnabled
    ) {
        if (groupEnabled != null) this.groupEnabled = groupEnabled;
        if (roundEnabled != null) this.roundEnabled = roundEnabled;
        if (communityEnabled != null) this.communityEnabled = communityEnabled;
        if (reportEnabled != null) this.reportEnabled = reportEnabled;
        if (pushEnabled != null) this.pushEnabled = pushEnabled;
    }

    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }
}