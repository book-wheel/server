package com.bookwheel.server.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "내가 차단한 사용자")
public record UserBlockResponse(
    @Schema(description = "차단 대상 userPK. 탈퇴 후에도 차단 해제에 사용") String userPK,
    @Schema(description = "닉네임. 탈퇴 시 '탈퇴한 사용자'") String nickname,
    @Schema(description = "프로필 이미지 URL. 이미지가 없거나 탈퇴 시 null", nullable = true) String profileImageUrl,
    @Schema(description = "차단 시각") LocalDateTime blockedAt
) {
}
