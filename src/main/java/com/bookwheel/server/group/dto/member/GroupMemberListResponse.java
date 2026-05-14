package com.bookwheel.server.group.dto.member;

import java.util.List;

public record GroupMemberListResponse(
        int totalCount,
        List<GroupMemberResponse> members
) {
    public static GroupMemberListResponse from(List<GroupMemberResponse> members) {
        return new GroupMemberListResponse(members.size(), members);
    }
}