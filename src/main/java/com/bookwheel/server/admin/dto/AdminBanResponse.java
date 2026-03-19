package com.bookwheel.server.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
@Schema(description = "회원 제재 결과 정보")
public record AdminBanResponse(
    @Schema(description = "유저 PK", example = "d290f1ee-6c54-4b01-90e6-d701748f0851")

    String userPk,

    @Schema(description = "닉네임", example = "으내으내으내")
    String nickname,

    @Schema(description = "현재 상태", example = "BANNED")
    String status,

    @Schema(description = "제재 유형", example = "SEVEN_DAYS")
    String banType,

    @Schema(description = "제재 사유 (한글 메시지)", example = "책을 보내지 않음")
    String reasonMessage,

    @Schema(description = "제재 일시")
    LocalDateTime bannedAt,

    @Schema(description = "정지 해제 예정일 (영구 정지면 9999년)")
    LocalDateTime releaseDate
) {}
