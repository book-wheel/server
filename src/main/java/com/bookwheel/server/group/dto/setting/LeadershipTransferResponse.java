package com.bookwheel.server.group.dto.setting;

import com.bookwheel.server.member.enums.MemberRole;
import lombok.Builder;

@Builder
public record LeadershipTransferResponse(
        String groupId,
        String newLeaderUserPK,
        MemberRole newLeaderRole,
        String previousLeaderUserPK,
        MemberRole previousLeaderRole
) {
    public static LeadershipTransferResponse of(
            String groupId,
            String newLeaderUserPK,
            MemberRole newLeaderRole,
            String previousLeaderUserPK,
            MemberRole previousLeaderRole
    ) {
        return LeadershipTransferResponse.builder()
                .groupId(groupId)
                .newLeaderUserPK(newLeaderUserPK)
                .newLeaderRole(newLeaderRole)
                .previousLeaderUserPK(previousLeaderUserPK)
                .previousLeaderRole(previousLeaderRole)
                .build();
    }
}
