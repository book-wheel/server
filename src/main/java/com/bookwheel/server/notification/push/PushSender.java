package com.bookwheel.server.notification.push;

import com.bookwheel.server.notification.entity.Notification;

import java.util.List;

/**
 * Delivers persisted notifications to a push provider.
 */
public interface PushSender {

    void send(String expoPushToken, Notification notification);

    /**
     * Keeps each token paired with its notification so recipient-specific metadata is preserved.
     */
    void sendBatch(List<PushTarget> targets);
}
