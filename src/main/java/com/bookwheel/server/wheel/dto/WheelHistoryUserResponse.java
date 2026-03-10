package com.bookwheel.server.wheel.dto;

import com.bookwheel.server.wheel.entity.WheelState;
import com.bookwheel.server.wheel.entity.WheelStateImage;

import java.time.LocalDateTime;
import java.util.List;

public record WheelHistoryUserResponse(
    String wheelStateId,
    String bookTitle,
    int roundNumber,
    List<String> authImageUrls,
    String reviewText,
    LocalDateTime reviewAt
) {
    public static WheelHistoryUserResponse of(WheelState wheelState, int roundNumber) {
        return new WheelHistoryUserResponse(
                wheelState.getWheelStateId(),
                wheelState.getOwnBook().getBook().getTitle(),
                roundNumber,
                wheelState.getAuthImages().stream()
                        .map(WheelStateImage::getImageUrl)
                        .toList(),
                wheelState.getReviewText(),
                wheelState.getReviewedAt()
        );
    }
}
