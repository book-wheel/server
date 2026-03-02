package com.bookwheel.server.user.dto;

public record IdRecoveryResponse(
        String userId // 마스킹 처리된 아이디 (예: hye****)
) {
    public static IdRecoveryResponse from(String maskedUserId) {
        return new IdRecoveryResponse(maskedUserId);
    }
}