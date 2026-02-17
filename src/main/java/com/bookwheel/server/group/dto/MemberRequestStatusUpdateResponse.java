package com.bookwheel.server.group.dto;

import lombok.Builder;

@Builder
public record MemberRequestStatusUpdateResponse(
        String memberId,
        MemberRequestStatus status
) {
    public static MemberRequestStatusUpdateResponse of(String memberId, MemberRequestStatus status) {
        return MemberRequestStatusUpdateResponse.builder()
                .memberId(memberId)
                .status(status)
                .build();
    }
}
