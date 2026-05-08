package com.bookwheel.server.group.dto.setting;

import com.bookwheel.server.member.enums.MemberStatus;

public record MemberKickResponse(
        String memberId,
        MemberStatus status
) {
    public static MemberKickResponse of(String memberId, MemberStatus status) {
        return new MemberKickResponse(memberId, status);
    }
}
