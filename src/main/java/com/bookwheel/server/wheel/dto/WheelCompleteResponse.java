package com.bookwheel.server.wheel.dto;

import com.bookwheel.server.wheel.enums.WheelStatus;
import lombok.Builder;

@Builder
public record WheelCompleteResponse(
        String wheelStateId,
        boolean isCompleted,
        WheelStatus status
) {
    public static WheelCompleteResponse of(String wheelStateId, boolean isCompleted, WheelStatus status) {
        return WheelCompleteResponse.builder()
                .wheelStateId(wheelStateId)
                .isCompleted(isCompleted)
                .status(status)
                .build();
    }
}