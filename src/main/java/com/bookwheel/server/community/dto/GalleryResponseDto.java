package com.bookwheel.server.community.dto;

import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.entity.PostImage;

import java.time.LocalDateTime;
import java.util.List;

public record GalleryResponseDto(
    Long postId,
    Long bookId,
    String isbn,
    String thumbnailUrl,
    int imageCount,
    LocalDateTime createdAt
) {
    public static GalleryResponseDto from(Post post) {
        List<PostImage> images = post.getImages();

        return new GalleryResponseDto(
            post.getPostId(),
            post.getBookInfo().getBookInfoId(),
            post.getBookInfo().getIsbn(),
            getThumbnailUrl(images),
            images == null ? 0 : images.size(),
            post.getCreatedAt()
        );
    }

    private static String getThumbnailUrl(List<PostImage> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }

        return images.get(0).getObjectKey();
    }
}
