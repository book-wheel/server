package com.bookwheel.server.group.dto.member;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record GroupMemberListResponse(
        int totalCount,

        @Schema(description = "현재 진행 중인 라운드. 진행 중인 라운드가 없으면 null", nullable = true)
        GroupCurrentRoundResponse currentRound,

        List<GroupMemberResponse> members
) {
    public static GroupMemberListResponse from(
            GroupCurrentRoundResponse currentRound,
            List<GroupMemberResponse> members
    ) {
        return new GroupMemberListResponse(members.size(), currentRound, members);
    }
}