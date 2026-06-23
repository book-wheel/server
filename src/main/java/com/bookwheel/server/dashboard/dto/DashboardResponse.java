package com.bookwheel.server.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;

@Builder
@Schema(description = "그룹 대시보드 메인 응답")
public record DashboardResponse(
        @Schema(description = "그룹 이름", example = "소전카르텔")
        String groupName,

        @Schema(description = "현재 라운드 번호. 시작 전에는 0입니다.", example = "0")
        Integer currentRound,

        @Schema(description = "전체 라운드 수", example = "6")
        Integer totalRound,

        @Schema(description = "현재 라운드 시작일. 시작 전에는 예정 시작일입니다.", example = "2026-06-25")
        LocalDate startDate,

        @Schema(description = "현재 라운드 종료일. 시작 전에는 예정 시작일입니다.", example = "2026-06-25")
        LocalDate endDate,

        @Schema(description = "종료일 또는 예정 시작일까지 남은 일수", example = "1")
        Integer dDay,

        @Schema(description = "내가 읽을 책 정보. 시작 전에도 배정 계산이 가능하면 반환합니다.", nullable = true)
        MyStepResponse myStep,

        @Schema(description = "내가 등록한 책의 전달 상태. 책 미등록이면 null입니다.", nullable = true)
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
