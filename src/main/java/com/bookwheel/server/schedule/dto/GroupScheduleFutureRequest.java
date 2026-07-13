package com.bookwheel.server.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "진행 중 그룹의 미래 독서 일정 재생성 요청")
public record GroupScheduleFutureRequest(
        @Schema(description = "보호된 라운드를 포함한 최종 전체 라운드 수", example = "5")
        @NotNull(message = "전체 라운드 수를 입력해주세요.")
        Integer totalRoundCount,

        @Schema(description = "미래 일정의 최대 종료일 제한. 선택값이며 없으면 제한 없이 계산합니다.", example = "2026-07-31", nullable = true)
        LocalDate endDate,

        @Schema(description = "미래 일정에서 제외할 개별 날짜 목록", example = "[\"2026-06-28\", \"2026-07-01\"]", nullable = true)
        List<LocalDate> excludedDates,

        @Schema(description = "미래 일정에서 제외할 날짜 범위 목록. 시작일과 종료일을 포함합니다.", nullable = true)
        List<ExcludedDateRange> excludedDateRanges
) {
}
