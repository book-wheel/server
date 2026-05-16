package com.bookwheel.server.notification.dto;

import com.bookwheel.server.notification.entity.Notification;
import com.bookwheel.server.notification.enums.NotificationCategory;
import com.bookwheel.server.notification.enums.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        NotificationType type,
        NotificationCategory category,
        String title,
        String body,
        String deepLink,
        boolean isRead,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getCategory(),
                notification.getTitle(),
                notification.getBody(),
                notification.getDeepLink(),
                Boolean.TRUE.equals(notification.getIsRead()),
                notification.getCreatedAt(),
                notification.getReadAt()
        );
    }
}