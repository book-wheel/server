package com.bookwheel.server.book.dto;

import lombok.Builder;

@Builder
public record OwnBookRegisterResponse(
        String ownBookId
) {
    public static OwnBookRegisterResponse of(String ownBookId) {
        return OwnBookRegisterResponse.builder()
                .ownBookId(ownBookId)
                .build();
    }
}
