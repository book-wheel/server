package com.bookwheel.server.notification.push;

import com.bookwheel.server.notification.entity.Notification;

public record PushTarget(
        String expoPushToken,
        Notification notification
) {
}
