package com.bookwheel.server.wheel.dto;

import com.bookwheel.server.wheel.entity.WheelState;
import com.bookwheel.server.wheel.entity.WheelStateImage;

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
    public static HistoryDto of(WheelState ws, int roundNumber) {
        return new HistoryDto(
                ws.getWheelStateId(),
                roundNumber,
                ws.getMember().getUser().getNickname(),
                ws.getAuthImages().stream()
                        .map(WheelStateImage::getImageUrl)
                        .toList(),
                ws.getReviewText(),
                ws.getReviewedAt()
        );
    }
}
