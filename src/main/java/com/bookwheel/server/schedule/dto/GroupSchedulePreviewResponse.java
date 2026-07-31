package com.bookwheel.server.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "저장하지 않고 계산한 그룹 독서 일정 미리보기")
public record GroupSchedulePreviewResponse(
        @Schema(description = "전체 날짜 틀 계산에 사용한 목표 인원", example = "10")
        int targetMemberCount,

        @Schema(description = "목표 인원 기준 전체 라운드 수", example = "9")
        int plannedRoundCount,

        @Schema(description = "계산된 마지막 라운드 종료일", example = "2026-10-11")
        LocalDate plannedEndDate,

        @Schema(description = "저장 예정인 라운드별 날짜")
        List<GroupScheduleRoundResponse> rounds
) {
}
