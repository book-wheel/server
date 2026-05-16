package com.bookwheel.server.community.event;

public record PostLikedEvent(
        Long postId,
        String postOwnerUserId,
        String likerUserId,
        String likerNickname
) {
}