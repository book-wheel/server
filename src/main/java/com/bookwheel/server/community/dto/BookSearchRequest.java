package com.bookwheel.server.community.dto;

public record BookSearchRequest(
    String query,
    String sort,
    Integer page,
    Integer size
) {
    public BookSearchRequest {
        if (sort == null) sort = "accuracy";
        if (page == null || page < 1) page = 1;
        if (size == null || size < 1) size = 10;
    }
}
