package com.bookwheel.server.group.dto.setting;

import com.bookwheel.server.member.enums.MemberStatus;

public record MemberKickResponse(
        String targetUserPK,
        MemberStatus status
) {
    public static MemberKickResponse of(String targetUserPK, MemberStatus status) {
        return new MemberKickResponse(targetUserPK, status);
    }
}
