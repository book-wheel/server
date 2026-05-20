package com.bookwheel.server.notification.entity;

import com.bookwheel.server.notification.enums.NotificationCategory;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@Table(
        name = "notification_preference",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_pref_user", columnNames = "user_pk")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "preference_id")
    private Long id;

    @Column(name = "user_pk", length = 50, nullable = false)
    private String userPK;

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
    @Column(name = "push_enabled", nullable = false)
    private Boolean pushEnabled = true;

    @Column(name = "fcm_token", length = 255)
    private String fcmToken;

    public static NotificationPreference defaultsFor(String userPK) {
        return NotificationPreference.builder()
                .userPK(userPK)
                .build();
    }

    public boolean allows(NotificationCategory category) {
        return switch (category) {
            case GROUP -> Boolean.TRUE.equals(groupEnabled);
            case ROUND -> Boolean.TRUE.equals(roundEnabled);
            case COMMUNITY -> Boolean.TRUE.equals(communityEnabled);
            // 제재/계정 카테고리는 본인 상태와 직결되는 보안성 알림이라 사용자가 끄더라도 강제 발송
            case REPORT, ACCOUNT -> true;
        };
    }

    /**
     * 푸시 발송 허용 여부. REPORT/ACCOUNT 는 pushEnabled 설정과 무관하게 강제 발송.
     * 그 외 카테고리는 사용자가 푸시 전체 toggle 을 끄면 인앱만 표시되고 푸시는 안 감.
     */
    public boolean allowsPush(NotificationCategory category) {
        if (category == NotificationCategory.REPORT || category == NotificationCategory.ACCOUNT) {
            return true;
        }
        return Boolean.TRUE.equals(pushEnabled);
    }

    public void updateCategoryFlags(
            Boolean groupEnabled,
            Boolean roundEnabled,
            Boolean communityEnabled,
            Boolean pushEnabled
    ) {
        if (groupEnabled != null) this.groupEnabled = groupEnabled;
        if (roundEnabled != null) this.roundEnabled = roundEnabled;
        if (communityEnabled != null) this.communityEnabled = communityEnabled;
        if (pushEnabled != null) this.pushEnabled = pushEnabled;
    }

    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }
}
