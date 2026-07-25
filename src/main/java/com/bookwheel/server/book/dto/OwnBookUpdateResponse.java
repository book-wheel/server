package com.bookwheel.server.book.dto;

import lombok.Builder;

@Builder
public record OwnBookUpdateResponse(
        String ownBookId
) {
    public static OwnBookUpdateResponse of(String ownBookId) {
        return OwnBookUpdateResponse.builder()
                .ownBookId(ownBookId)
                .build();
    }
}
