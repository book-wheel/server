package com.bookwheel.server.group.dto;

import com.bookwheel.server.member.enums.MemberStatus;
import lombok.Builder;

@Builder
public record GroupJoinResponse(
        String memberId,
        MemberStatus status
) {
    public static GroupJoinResponse of(String memberId, MemberStatus status) {
        return GroupJoinResponse.builder()
                .memberId(memberId)
                .status(status)
                .build();
    }
}
