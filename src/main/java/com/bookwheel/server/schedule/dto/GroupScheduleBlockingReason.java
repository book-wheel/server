package com.bookwheel.server.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "독서 모임 시작을 막는 사유")
public enum GroupScheduleBlockingReason {
    // 목표 인원은 일정 틀의 상한이며, 실제 시작에는 최소 2명의 ACTIVE 멤버가 필요하다.
    MINIMUM_ACTIVE_MEMBER_COUNT_NOT_REACHED,
    // 목표 인원보다 많은 멤버가 들어간 비정상 상태에서는 배정표를 확정하지 않는다.
    TARGET_MEMBER_COUNT_EXCEEDED,
    // 시작 시점의 ACTIVE 멤버는 모두 참여 도서를 등록해야 한다.
    MEMBER_BOOK_NOT_REGISTERED,
    // 목표 인원 기준으로 미리 만든 라운드 날짜 틀이 없거나 손상된 상태다.
    ROUND_PLAN_INCOMPLETE,
    // 현재 인원으로 실행할 라운드의 멤버별 PLANNED 배정이 완성되지 않은 상태다.
    ASSIGNMENT_INCOMPLETE,
    // 예정 시작일이 지나 새 미래 시작일로 교체해야 하는 상태다.
    START_DATE_PASSED
}
