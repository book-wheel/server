package com.bookwheel.server.admin.dto;

import com.bookwheel.server.admin.entity.Admin;
import lombok.Builder;

@Builder
public record AdminLoginResponse(
        String accessToken,
        String refreshToken,
        String adminPK,
        String loginId,
        String name
) {
    public static AdminLoginResponse of(Admin admin, String accessToken, String refreshToken) {
        return AdminLoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .adminPK(admin.getAdminPK())
                .loginId(admin.getLoginId())
                .name(admin.getName())
                .build();
    }
}
