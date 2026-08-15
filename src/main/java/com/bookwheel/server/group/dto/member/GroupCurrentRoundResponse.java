package com.bookwheel.server.group.dto.member;

import com.bookwheel.server.schedule.entity.Round;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현재 진행 중인 라운드 정보")
public record GroupCurrentRoundResponse(
        @Schema(description = "현재 라운드 ID", example = "round-uuid-123")
        String roundId,

        @Schema(description = "현재 라운드 회차", example = "2")
        Integer roundNumber
) {
    public static GroupCurrentRoundResponse from(Round round) {
        return new GroupCurrentRoundResponse(round.getRoundId(), round.getRoundNumber());
    }
}
