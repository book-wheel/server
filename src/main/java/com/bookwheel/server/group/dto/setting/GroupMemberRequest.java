package com.bookwheel.server.group.dto.setting;

import com.bookwheel.server.member.enums.MemberRole;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record GroupMemberRequest(
        @NotNull String userPK,
        @NotNull MemberRole role
) {
}