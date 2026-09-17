package com.bookwheel.server.admin.dto;

import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.entity.PostImage;
import com.bookwheel.server.community.support.CommunityAuthorDisplay;
import com.bookwheel.server.user.entity.User;
import org.springframework.util.StringUtils;

public record AdminPostResponse(
    Long postId,
    String objectKey,
    String isbn,
    String uploaderNickname,
    String uploaderPK
) {
    public static AdminPostResponse from(Post post) {

        // 갤러리와 같은 규칙으로 축소본을 우선 쓰고, 썸네일 도입 이전 이미지만 원본으로 폴백한다.
        String thumbnailKey = post.getImages().isEmpty() ? null : representativeKey(post.getImages().get(0));
        User uploader = post.getUploader();

        return new AdminPostResponse(
            post.getPostId(),
            thumbnailKey,// 대표사진 한장만 전송.
            post.getBookInfo().getIsbn(),
            CommunityAuthorDisplay.displayName(uploader),
            uploader != null ? uploader.getId() : null
        );
    }

    private static String representativeKey(PostImage image) {
        return StringUtils.hasText(image.getThumbnailKey())
            ? image.getThumbnailKey()
            : image.getObjectKey();
    }
}
