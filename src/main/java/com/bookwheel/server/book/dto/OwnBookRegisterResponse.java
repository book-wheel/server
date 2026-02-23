package com.bookwheel.server.book.dto;

import lombok.Builder;

@Builder
public record OwnBookRegisterResponse(
        String ownbookId
) {
    public static OwnBookRegisterResponse of(String ownbookId) {
        return OwnBookRegisterResponse.builder()
                .ownbookId(ownbookId)
                .build();
    }
}
