package com.bookwheel.server.community.event;

public record PostCommentedEvent(
        Long postId,
        String postOwnerUserId,
        String commenterUserId,
        String commenterNickname,
        String commentPreview
) {
}