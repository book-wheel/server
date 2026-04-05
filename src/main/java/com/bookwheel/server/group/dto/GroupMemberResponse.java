package com.bookwheel.server.group.dto;

import com.bookwheel.server.member.enums.MemberRole;
import lombok.Builder;

@Builder
public record GroupMemberResponse(
        Long userPK,
        String nickname,
        String profileImageUrl,
        MemberRole role
) {
}