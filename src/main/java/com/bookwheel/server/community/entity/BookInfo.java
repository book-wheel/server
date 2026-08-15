package com.bookwheel.server.community.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BookInfo {

    // 외부 API(알라딘 · 도서관정보나루) 값을 그대로 저장하므로 원본이 들어갈 만큼 길이를 잡는다.
    // 특히 저자는 "안나 밀보른 (지은이), 호밀로스 (그림), 김지선 (옮긴이)"처럼 역할이 나열돼
    // 알라딘 실측(173종)에서 최대 504자까지 나왔다. 인기대출 도서는 제목 500 / 저자 500 / 표지 1000 으로 저장한다.
    public static final int TITLE_MAX_LENGTH = 500;
    public static final int AUTHOR_MAX_LENGTH = 1000;
    public static final int COVER_IMAGE_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_info_id")
    private Long bookInfoId;


    @Column(name = "isbn", length = 20, nullable = false, unique = true)
    private String isbn;

    // 게시글 작성 시점에 전달받은 도서 제목. 상세 조회에서 외부 API 없이 제목을 내려주기 위해 저장한다.
    @Column(name = "title", length = TITLE_MAX_LENGTH)
    private String title;

    // 관심 도서 등록 시점에 저장해 두는 저자/표지. 관심 도서 목록에서 외부 API 없이 표지를 내려주기 위한 값이다.
    @Column(name = "author", length = AUTHOR_MAX_LENGTH)
    private String author;

    @Column(name = "cover_image", length = COVER_IMAGE_MAX_LENGTH)
    private String coverImage;

    // 같은 ISBN의 BookInfo는 게시글 간에 재사용되므로, 제목이 비어 있을 때만 채운다.
    public void applyTitleIfAbsent(String title) {
        if (!hasText(this.title) && hasText(title)) {
            this.title = truncate(title, TITLE_MAX_LENGTH);
        }
    }

    // 이미 저장된 값은 유지하고 비어 있는 항목만 채운다. 같은 ISBN을 여러 사용자가 공유하기 때문이다.
    public void applyBookDetailsIfAbsent(String title, String author, String coverImage) {
        applyTitleIfAbsent(title);

        if (!hasText(this.author) && hasText(author)) {
            this.author = truncate(author, AUTHOR_MAX_LENGTH);
        }

        // 표지는 URL이라 잘라 두면 깨진 이미지가 되므로, 길이를 넘으면 자르지 않고 저장하지 않는다.
        // 비어 있으면 관심 도서 목록이 book 테이블 값으로 대체한다.
        if (!hasText(this.coverImage) && hasText(coverImage) && coverImage.length() <= COVER_IMAGE_MAX_LENGTH) {
            this.coverImage = coverImage;
        }
    }

    // 외부 API 값은 길이 상한이 없어 컬럼을 넘길 수 있다. 저장 실패로 이어지지 않도록 경계에서 자른다.
    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }

        // 서로게이트 쌍 중간을 자르면 깨진 문자가 남으므로 한 글자 더 줄인다.
        int end = Character.isHighSurrogate(value.charAt(maxLength - 1)) ? maxLength - 1 : maxLength;
        return value.substring(0, end);
    }

    // 관심 도서 목록에 필요한 기본 메타데이터가 모두 있어야 외부 조회를 다시 시도하지 않는다.
    public boolean hasBookDetails() {
        return hasText(title) && hasText(author) && hasText(coverImage);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
