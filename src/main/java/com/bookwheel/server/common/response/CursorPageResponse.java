package com.bookwheel.server.common.response;

import java.util.List;

public record CursorPageResponse<T> (
    List<T> content,
    int size,
    long totalElements,
    boolean hasNext,
    String nextCursor//없으면 null
)
{
    public static <T> CursorPageResponse<T> of(
        List<T> content,
        int size,
        long totalElements,
        boolean hasNext,
        String nextCursor
    ) {
        return new CursorPageResponse<>(content, size, totalElements, hasNext, nextCursor);
    }

}
