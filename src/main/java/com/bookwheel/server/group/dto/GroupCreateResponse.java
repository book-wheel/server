package com.bookwheel.server.group.dto;

import lombok.Builder;

@Builder
public record GroupCreateResponse(
        String groupId
) {
    public static GroupCreateResponse of(String groupId) {
        return GroupCreateResponse.builder()
                .groupId(groupId)
                .build();
    }
}