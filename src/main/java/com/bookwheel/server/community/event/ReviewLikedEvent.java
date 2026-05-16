package com.bookwheel.server.community.event;

public record ReviewLikedEvent(
        Long reviewId,
        String reviewerUserId,
        String likerUserId,
        String likerNickname
) {
}