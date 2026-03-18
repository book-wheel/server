package com.bookwheel.server.community.dto;

import com.bookwheel.server.community.entity.BookInfo;

import java.util.UUID;

public record AladinItemDto(
    String title,
    String author,
    String publisher,
    String pubDate,
    String description,
    String isbn13,
    String cover
) {
    public BookInfo toEntity() {
        return BookInfo.builder()
            .id(UUID.randomUUID().toString())
            .isbn(this.isbn13)
            .title(this.title)
            .author(this.author)
            .publisher(this.publisher)
            .coverImageUrl(this.cover)
            .description(this.description)
            .publishedDate(this.pubDate)
            .build();
    }
}
