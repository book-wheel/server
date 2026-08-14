package com.bookwheel.server.notification.push;

public record ExpoPushReceiptRegistration(
        String receiptId,
        String expoPushToken,
        Long notificationId
) {
}
