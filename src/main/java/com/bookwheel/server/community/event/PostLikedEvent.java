package com.bookwheel.server.community.event;

public record PostLikedEvent(
        Long postId,
        String postOwnerUserPK,
        String likerUserPK,
        String likerNickname
) {
}
