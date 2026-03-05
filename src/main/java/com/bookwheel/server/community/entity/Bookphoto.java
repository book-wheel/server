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
public class Bookphoto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "photo_id")
    private Long photoId;

    // Book 엔티티 참조 (N:1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", referencedColumnName = "book_id", nullable = false)
    private Book book;

    // 업로드한 사용자 참조 (N:1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User uploader;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;
}
