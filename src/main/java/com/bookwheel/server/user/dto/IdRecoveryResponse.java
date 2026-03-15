package com.bookwheel.server.user.dto;

public record IdRecoveryResponse(
        String userId
) {
    public static IdRecoveryResponse from(String maskedUserId) {
        return new IdRecoveryResponse(maskedUserId);
    }
}