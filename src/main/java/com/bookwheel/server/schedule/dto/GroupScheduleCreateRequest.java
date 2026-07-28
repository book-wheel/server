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

        @Schema(description = "일정이 넘을 수 없는 종료 제한일. 선택값이며 없으면 제한 없이 계산합니다.", example = "2026-07-31", nullable = true)
        LocalDate scheduleDeadline,

        @Schema(description = "제외할 개별 날짜 목록", example = "[\"2026-06-28\", \"2026-07-01\"]", nullable = true)
        List<LocalDate> excludedDates,

        @Schema(description = "제외할 날짜 범위 목록. 시작일과 종료일을 포함합니다.", nullable = true)
        List<@Valid ExcludedDateRange> excludedDateRanges,

        // 목표 인원은 시작 필수 인원이 아니라 날짜 틀의 크기와 모집 상한을 정하는 값이다.
        @Schema(description = "전체 라운드 날짜 틀과 모집 상한에 사용할 목표 인원. 현재 ACTIVE 멤버 수 이상, 모임 최대 인원 이하이며 목표 인원을 다 채우지 않아도 현재 인원 기준으로 시작할 수 있습니다.", example = "10")
        @NotNull(message = "목표 인원을 입력해주세요.")
        @Min(value = 2, message = "목표 인원은 최소 2명 이상이어야 합니다.")
        Integer targetMemberCount
) {
}
