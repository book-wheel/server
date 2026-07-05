package com.bookwheel.server.community.event;

public record PostCommentedEvent(
        Long postId,
        String postOwnerUserPK,
        String commenterUserPK,
        String commenterNickname,
        String commentPreview
) {
}
