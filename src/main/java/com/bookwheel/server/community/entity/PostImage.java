package com.bookwheel.server.community.entity;

import com.bookwheel.server.book.entity.Book;
import com.bookwheel.server.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "post_images")
public class PostImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_image_id")
    private Long postImageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post; // 어느 게시물에 속한 사진인지

    @Column(name = "fileExtensions", nullable = false, length = 500)
    private String objectKey;

    // 갤러리 목록에 쓰는 축소본의 objectKey.
    // 썸네일 도입 이전에 올라온 이미지는 null 이며, 이때는 원본(objectKey)으로 폴백한다.
    @Column(name = "thumbnail_key", length = 500)
    private String thumbnailKey;

    public void setPost(Post post) {
        this.post = post;
    }

    // 썸네일 도입 이전에 올라온 이미지를 나중에 채워 넣기 위한 경로다.
    public void applyThumbnailKey(String thumbnailKey) {
        this.thumbnailKey = thumbnailKey;
    }
}
