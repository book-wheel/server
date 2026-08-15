package com.bookwheel.server.notification.push;

public record ExpoPushReceiptResult(
        String status,
        String errorCode,
        String message
) {
}
