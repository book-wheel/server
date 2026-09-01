package com.bookwheel.server.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;

@Builder
@Schema(description = "홈 현재·예정 교환독서 모임 카드")
public record GroupReadingCardResponse(
        @Schema(description = "그룹 ID", example = "group-1")
        String groupId,

        @Schema(description = "그룹 이름", example = "독서 모임")
        String groupName,

        @Schema(
                description = "카드 상태: scheduled, active, reschedule_required",
                example = "scheduled"
        )
        String status,

        @Schema(description = "현재 회차. 시작 전이거나 현재 회차가 없으면 0입니다.", example = "0")
        Integer currentRound,

        @Schema(description = "전체 회차 수", example = "5")
        Integer totalRound,

        @Schema(description = "예정 시작일 또는 현재 회차 시작일", nullable = true)
        LocalDate startDate,

        @Schema(description = "예정 시작일 또는 현재 회차 종료일", nullable = true)
        LocalDate endDate,

        @Schema(description = "시작일 또는 현재 회차 종료일까지 남은 일수", nullable = true)
        Integer dDay,

        @Schema(description = "내가 읽을 예정이거나 현재 읽는 책", nullable = true)
        MyStepResponse myStep,

        @Schema(description = "내가 모임에 등록한 책", nullable = true)
        MyBookStepResponse myBookStep
) {
}
