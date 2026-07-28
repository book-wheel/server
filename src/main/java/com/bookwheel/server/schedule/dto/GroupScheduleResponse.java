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

        @Schema(description = "일정이 넘을 수 없는 종료 제한일", example = "2026-08-31", nullable = true)
        LocalDate scheduleDeadline,

        @Schema(description = "제외할 개별 날짜 목록")
        List<LocalDate> excludedDates,

        @Schema(description = "제외할 날짜 범위 목록")
        List<ExcludedDateRange> excludedDateRanges,

        @Schema(
                description = "일정 상태. CONFIGURED는 날짜 틀은 생성됐지만 현재 인원 기준 PLANNED 배정이 미완성, " +
                        "READY는 현재 ACTIVE 멤버가 2명 이상이고 전원 도서 및 실행 라운드 배정 준비 완료, " +
                        "RESCHEDULE_REQUIRED는 시작일을 놓친 상태입니다."
        )
        GroupScheduleStatus scheduleStatus,

        @Schema(description = "전체 라운드 날짜 틀을 만드는 기준이자 시작 전 모집 가능한 상한 인원", example = "10", nullable = true)
        Integer targetMemberCount,

        @Schema(description = "현재 ACTIVE 멤버 수", example = "7")
        int currentMemberCount,

        @Schema(description = "현재 ACTIVE 인원 기준 N-1개 실행 라운드의 PLANNED 배정이 준비돼 시작 가능한지 여부")
        boolean canStart,

        @Schema(description = "시작 준비를 막는 사유 목록")
        List<GroupScheduleBlockingReason> blockingReasons,

        @Schema(description = "참여 도서를 등록하지 않은 ACTIVE 멤버 목록")
        List<GroupScheduleMissingBookMemberResponse> missingBookMembers,

        @Schema(description = "DB에 보존된 전체 날짜 틀의 라운드 수", example = "9")
        int plannedRoundCount,

        @Schema(description = "현재 상태에서 실제 실행 대상으로 확정된 라운드 수", example = "2")
        int executableRoundCount,

        @Schema(description = "전체 날짜 틀의 마지막 종료일", example = "2026-10-11", nullable = true)
        LocalDate plannedEndDate,

        @Schema(description = "실제 마지막 실행 라운드 종료일", example = "2026-08-14", nullable = true)
        LocalDate executableEndDate,

        @Schema(description = "라운드별 날짜와 내 책바퀴 배정. executable=false인 라운드는 보존된 비활성 날짜 틀입니다.")
        List<GroupScheduleAssignmentResponse> rounds
) {
}
