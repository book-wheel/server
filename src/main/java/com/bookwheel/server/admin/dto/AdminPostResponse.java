package com.bookwheel.server.admin.dto;

import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.entity.PostImage;

public record AdminPostResponse(
    Long postId,
    String objectKey,
    String isbn,
    String uploaderNickname,
    String uploaderPK
) {
    public static AdminPostResponse from(Post post) {

        String thumbnailKey = post.getImages().isEmpty() ? null : post.getImages().get(0).getObjectKey();

        return new AdminPostResponse(
            post.getPostId(),
            thumbnailKey,// 대표사진 한장만 전송.
            post.getBookInfo().getIsbn(),
            post.getUploader().getNickname(),
            post.getUploader().getId()
        );
    }
}
