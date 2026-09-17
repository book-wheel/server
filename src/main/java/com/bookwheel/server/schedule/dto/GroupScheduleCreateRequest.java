package com.bookwheel.server.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "그룹 독서 일정 생성 요청")
public record GroupScheduleCreateRequest(
        @Schema(description = "일정 시작일", example = "2026-02-01")
        @NotNull(message = "시작 일을 입력해주세요.")
        LocalDate startDate,

        // 모집 중 전체 날짜 틀의 각 라운드 계산에 사용할 독서 기간이다.
        @Schema(description = "라운드별 독서 기간(일)", example = "7")
        @NotNull(message = "독서 기간을 입력해주세요.")
        @Min(value = 1, message = "독서 기간은 최소 1일 이상이어야 합니다.")
        Integer readingPeriod,

        @Schema(description = "일정이 넘을 수 없는 종료 제한일. 선택값이며 시작일로부터 3년 이내여야 합니다.", example = "2026-07-31", nullable = true)
        LocalDate endDate,

        @Schema(description = "제외할 개별 날짜 목록", example = "[\"2026-06-28\", \"2026-07-01\"]", nullable = true)
        List<LocalDate> excludedDates,

        @Schema(description = "제외할 날짜 범위 목록. 시작일과 종료일을 포함합니다.", nullable = true)
        List<@Valid ExcludedDateRange> excludedDateRanges,

        // 기존 클라이언트와의 하위 호환을 위해 필드는 받지만, 일정 목표 인원은 서버가 모임 최대 인원으로 결정한다.
        @Schema(description = "더 이상 사용하지 않음. 서버가 모임의 maxMembers를 일정 목표 인원으로 사용합니다.", deprecated = true, nullable = true)
        Integer targetMemberCount
) {
}
