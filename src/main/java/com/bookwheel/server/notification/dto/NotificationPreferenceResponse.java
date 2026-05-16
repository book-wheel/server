package com.bookwheel.server.notification.dto;

import com.bookwheel.server.notification.entity.NotificationPreference;

public record NotificationPreferenceResponse(
        boolean groupEnabled,
        boolean roundEnabled,
        boolean communityEnabled,
        boolean reportEnabled,
        boolean accountEnabled,
        boolean pushEnabled,
        String fcmToken
) {
    public static NotificationPreferenceResponse from(NotificationPreference preference) {
        return new NotificationPreferenceResponse(
                Boolean.TRUE.equals(preference.getGroupEnabled()),
                Boolean.TRUE.equals(preference.getRoundEnabled()),
                Boolean.TRUE.equals(preference.getCommunityEnabled()),
                Boolean.TRUE.equals(preference.getReportEnabled()),
                Boolean.TRUE.equals(preference.getAccountEnabled()),
                Boolean.TRUE.equals(preference.getPushEnabled()),
                preference.getFcmToken()
        );
    }
}