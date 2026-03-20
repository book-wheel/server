package com.bookwheel.server.admin.dto;

import com.bookwheel.server.community.entity.PostImage;

public record AdminPostResponse(
    Long photoId,
    String objectKey,
    String bookId,
    String uploaderNickname,
    String uploaderId
) {
    public static AdminPostResponse from(PostImage postImage) {
        return new AdminPostResponse(
            postImage.getPostImageId(),
            postImage.getObjectKey(),
            postImage.getPost().getBookInfo().getId(),
            postImage.getPost().getUploader().getNickname(),
            postImage.getPost().getUploader().getUserId()
        );
    }
}
