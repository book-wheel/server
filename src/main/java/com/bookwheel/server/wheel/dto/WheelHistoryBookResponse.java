package com.bookwheel.server.wheel.dto;

import com.bookwheel.server.book.entity.OwnBook;

import java.util.List;

public record WheelHistoryBookResponse(
        String ownBookId,
        String bookTitle,
        String author,
        String coverImageUrl,
        List<HistoryDto> histories
) {
    public static WheelHistoryBookResponse of(OwnBook ownBook, List<HistoryDto> histories) {
        return new WheelHistoryBookResponse(
                ownBook.getOwnBookId(),
                ownBook.getBook().getTitle(),
                ownBook.getBook().getAuthor(),
                ownBook.getBook().getCoverImage(),
                histories
        );
    }
}
