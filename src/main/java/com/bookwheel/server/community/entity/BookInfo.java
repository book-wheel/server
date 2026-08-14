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

    // 관심 도서 등록 시점에 저장해 두는 저자/표지. 관심 도서 목록에서 외부 API 없이 표지를 내려주기 위한 값이다.
    @Column(name = "author", length = 100)
    private String author;

    @Column(name = "cover_image", length = 255)
    private String coverImage;

    // 같은 ISBN의 BookInfo는 게시글 간에 재사용되므로, 제목이 비어 있을 때만 채운다.
    public void applyTitleIfAbsent(String title) {
        if (!hasText(this.title) && hasText(title)) {
            this.title = title;
        }
    }

    // 이미 저장된 값은 유지하고 비어 있는 항목만 채운다. 같은 ISBN을 여러 사용자가 공유하기 때문이다.
    public void applyBookDetailsIfAbsent(String title, String author, String coverImage) {
        applyTitleIfAbsent(title);

        if (!hasText(this.author) && hasText(author)) {
            this.author = author;
        }

        if (!hasText(this.coverImage) && hasText(coverImage)) {
            this.coverImage = coverImage;
        }
    }

    // 관심 도서 목록에 필요한 기본 메타데이터가 모두 있어야 외부 조회를 다시 시도하지 않는다.
    public boolean hasBookDetails() {
        return hasText(title) && hasText(author) && hasText(coverImage);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
