package com.bookwheel.server.group.dto.setting;

import com.bookwheel.server.member.enums.MemberStatus;

public record MemberExitResponse(
        String groupId,
        String userPK,
        MemberStatus status
) {
    public static MemberExitResponse of(String groupId, String userPK, MemberStatus status) {
        return new MemberExitResponse(groupId, userPK, status);
    }
}
