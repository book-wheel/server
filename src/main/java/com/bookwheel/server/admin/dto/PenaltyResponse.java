package com.bookwheel.server.admin.dto;

import com.bookwheel.server.admin.entity.Penalty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
@Schema(description = "패널티 이력 응답 정보")
public record PenaltyResponse(
    @Schema(description = "제재 유형", example = "SEVEN_DAYS")
    String banType,

    @Schema(description = "제재 사유", example = "책을 보내지 않음")
    String reasonMessage,

    @Schema(description = "제재 적용 일시")
    LocalDateTime bannedAt,

    @Schema(description = "제재 해제 예정일")
    LocalDateTime releaseDate
) {
    // 엔티티를 DTO로 편하게 바꿔주는 팩토리 메서드
    public static PenaltyResponse from(Penalty penalty) {
        return PenaltyResponse.builder()
            .banType(penalty.getBanType())
            .reasonMessage(penalty.getReasonMessage())
            .bannedAt(penalty.getBannedAt())
            .releaseDate(penalty.getReleaseDate())
            .build();
    }
}
