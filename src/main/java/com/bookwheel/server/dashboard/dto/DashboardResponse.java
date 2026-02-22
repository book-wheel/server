package com.bookwheel.server.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;

@Builder
@Schema(description = "그룹 대시보드 메인 응답")
public record DashboardResponse(
        @Schema(description = "그룹 이름", example = "소전카르텔")
        String groupName,

        @Schema(description = "현재 라운드 번호", example = "1")
        Integer currentRound,

        @Schema(description = "전체 라운드 수", example = "6")
        Integer totalRound,

        @Schema(description = "현재 라운드 시작일", example = "2026-02-01")
        LocalDate startDate,

        @Schema(description = "현재 라운드 종료일", example = "2026-02-17")
        LocalDate endDate,

        @Schema(description = "마감까지 남은 일수 (음수면 연체)", example = "10")
        Integer dDay,

        @Schema(description = "내 진행 단계 (없으면 null)", nullable = true)
        MyStepResponse myStep,

        @Schema(description = "내 책 진행 단계 (없으면 null)", nullable = true)
        MyBookStepResponse myBookStep
) {
    public static DashboardResponse of(
            String groupName,
            Integer currentRound,
            Integer totalRound,
            LocalDate startDate,
            LocalDate endDate,
            Integer dDay,
            MyStepResponse myStep,
            MyBookStepResponse myBookStep
    ) {
        return DashboardResponse.builder()
                .groupName(groupName)
                .currentRound(currentRound)
                .totalRound(totalRound)
                .startDate(startDate)
                .endDate(endDate)
                .dDay(dDay)
                .myStep(myStep)
                .myBookStep(myBookStep)
                .build();
    }
}
