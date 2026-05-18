package com.bookwheel.server.group.dto.setting;

import com.bookwheel.server.member.enums.MemberRole;
import lombok.Builder;

@Builder
public record MemberRoleChangeResponse(
        String groupId,
        String targetUserPK,
        MemberRole memberRole
) {
    public static MemberRoleChangeResponse of(String groupId, String targetUserPK, MemberRole memberRole) {
        return MemberRoleChangeResponse.builder()
                .groupId(groupId)
                .targetUserPK(targetUserPK)
                .memberRole(memberRole)
                .build();
    }
}
