package com.bookwheel.server.wheel.dto;

import com.bookwheel.server.wheel.entity.WheelState;

import java.time.LocalDateTime;
import java.util.List;

public record WheelHistoryUserResponse(
    String wheelStateId,
    String ownBookId,
    String bookTitle,
    String coverImageUrl,
    int roundNumber,
    List<String> authImageUrls,
    String reviewText,
    LocalDateTime reviewAt
) {
    public static WheelHistoryUserResponse of(WheelState wheelState, int roundNumber, List<String> authImageUrls) {
        return new WheelHistoryUserResponse(
                wheelState.getWheelStateId(),
                wheelState.getOwnBook().getOwnBookId(),
                wheelState.getOwnBook().getBook().getTitle(),
                wheelState.getOwnBook().getBook().getCoverImage(),
                roundNumber,
                authImageUrls,
                wheelState.getReviewText(),
                wheelState.getReviewedAt()
        );
    }
}
