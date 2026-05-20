package com.bookwheel.server.community.event;

public record ReviewLikedEvent(
        Long reviewId,
        String reviewerUserPK,
        String likerUserPK,
        String likerNickname
) {
}
