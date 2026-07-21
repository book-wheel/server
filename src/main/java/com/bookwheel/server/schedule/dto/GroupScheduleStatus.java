package com.bookwheel.server.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "일정 설정 상태")
public enum GroupScheduleStatus {
    NOT_CONFIGURED,
    CONFIGURED,
    READY,
    RESCHEDULE_REQUIRED,
    IN_PROGRESS,
    COMPLETE
}
