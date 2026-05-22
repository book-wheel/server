package com.bookwheel.server.community.dto;

public record BookLikeResponse(
    String isbn,
    boolean liked
) {
    public static BookLikeResponse of(String isbn, boolean liked) {
        return new BookLikeResponse(isbn, liked);
    }
}
