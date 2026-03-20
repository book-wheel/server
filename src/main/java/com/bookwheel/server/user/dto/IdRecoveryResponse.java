package com.bookwheel.server.user.dto;

public record IdRecoveryResponse(
        String loginId
) {
    public static IdRecoveryResponse from(String maskedUserId) {
        return new IdRecoveryResponse(maskedUserId);
    }
}