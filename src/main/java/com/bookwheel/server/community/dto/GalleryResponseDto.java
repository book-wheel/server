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
    public static GalleryResponseDto from(Post post, String thumbnailUrl) {
        List<PostImage> images = post.getImages();

        return new GalleryResponseDto(
            post.getPostId(),
            post.getBookInfo().getBookInfoId(),
            post.getBookInfo().getIsbn(),
            thumbnailUrl,
            images == null ? 0 : images.size(),
            post.getCreatedAt()
        );
    }

    // 대표 이미지(첫 번째)의 S3 objectKey를 반환한다. 이미지가 없으면 null.
    // 실제 노출용 URL 변환(Presigned)은 S3Service에 접근 가능한 서비스 레이어에서 수행한다.
    public static String thumbnailObjectKey(Post post) {
        List<PostImage> images = post.getImages();
        if (images == null || images.isEmpty()) {
            return null;
        }

        return images.get(0).getObjectKey();
    }
}
