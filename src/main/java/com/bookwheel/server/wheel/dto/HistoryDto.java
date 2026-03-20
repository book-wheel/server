package com.bookwheel.server.wheel.dto;

import com.bookwheel.server.wheel.entity.WheelState;

import java.time.LocalDateTime;
import java.util.List;

public record HistoryDto(
        String wheelStateId,
        int roundNumber,
        String readerName,
        List<String> authImageUrls,
        String reviewText,
        LocalDateTime completedAt
) {
    public static HistoryDto of(WheelState ws, int roundNumber, List<String> authImageUrls) {
        return new HistoryDto(
                ws.getWheelStateId(),
                roundNumber,
                ws.getMember().getUser().getNickname(),
                authImageUrls,
                ws.getReviewText(),
                ws.getReviewedAt()
        );
    }
}
