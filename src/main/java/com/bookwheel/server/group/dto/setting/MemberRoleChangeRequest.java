package com.bookwheel.server.group.dto.setting;

import com.bookwheel.server.member.enums.MemberRole;
import jakarta.validation.constraints.NotNull;

public record MemberRoleChangeRequest (
        @NotNull(message = "변경할 권한은 필수입니다.")
        MemberRole memberRole
) {
}
