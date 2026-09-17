package com.bookwheel.server.admin.dto;

import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.support.CommunityAuthorDisplay;
import com.bookwheel.server.user.entity.User;

public record AdminPostResponse(
    Long postId,
    String objectKey,
    String isbn,
    String uploaderNickname,
    String uploaderPK
) {
    public static AdminPostResponse from(Post post) {

        String thumbnailKey = post.getImages().isEmpty() ? null : post.getImages().get(0).getObjectKey();
        User uploader = post.getUploader();

        return new AdminPostResponse(
            post.getPostId(),
            thumbnailKey,// 대표사진 한장만 전송.
            post.getBookInfo().getIsbn(),
            CommunityAuthorDisplay.displayName(uploader),
            uploader != null ? uploader.getId() : null
        );
    }
}
