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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_info_id")
    private Long bookInfoId;


    @Column(name = "isbn", length = 20, nullable = false, unique = true)
    private String isbn;

    // 게시글 작성 시점에 전달받은 도서 제목. 상세 조회에서 외부 API 없이 제목을 내려주기 위해 저장한다.
    @Column(name = "title", length = 255)
    private String title;

    // 같은 ISBN의 BookInfo는 게시글 간에 재사용되므로, 제목이 비어 있을 때만 채운다.
    public void applyTitleIfAbsent(String title) {
        if (this.title == null && title != null && !title.isBlank()) {
            this.title = title;
        }
    }
}
