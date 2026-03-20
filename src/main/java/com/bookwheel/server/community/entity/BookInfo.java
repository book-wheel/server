package com.bookwheel.server.community.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BookInfo {

    @Id
    @Column(name = "book_info_id", length = 50)
    private String id;

    @Column(unique = true, nullable = false)
    private String isbn; // 알라딘 도서 고유 식별자

    @Column(nullable = false)
    private String title; // 책 제목

    private String author; // 저자

    private String publisher; // 출판사

    @Column(length = 1000)
    private String coverImageUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String publishedDate;
}
