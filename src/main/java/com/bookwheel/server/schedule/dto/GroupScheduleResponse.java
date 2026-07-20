package com.bookwheel.server.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "그룹 독서 일정 설정과 라운드 정보")
public record GroupScheduleResponse(
        @Schema(description = "예정 시작일", example = "2026-07-25", nullable = true)
        LocalDate startDate,

        @Schema(description = "라운드별 독서 기간(일)", example = "5", nullable = true)
        Integer readingPeriod,

        @Schema(description = "최대 종료일 제한", example = "2026-08-31", nullable = true)
        LocalDate endDate,

        @Schema(description = "제외할 개별 날짜 목록")
        List<LocalDate> excludedDates,

        @Schema(description = "제외할 날짜 범위 목록")
        List<ExcludedDateRange> excludedDateRanges,

        @Schema(
                description = "일정 상태. CONFIGURED는 설정만 저장됨, READY는 라운드 날짜 준비 완료, " +
                        "RESCHEDULE_REQUIRED는 예정일을 놓쳐 새 시작일 설정이 필요한 상태입니다."
        )
        GroupScheduleStatus scheduleStatus,

        @Schema(description = "라운드별 날짜와 내 책바퀴 배정. 배정 전에는 책 관련 필드가 null입니다.")
        List<GroupScheduleAssignmentResponse> rounds
) {
}
