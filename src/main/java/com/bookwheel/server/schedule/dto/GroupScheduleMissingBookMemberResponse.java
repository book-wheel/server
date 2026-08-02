package com.bookwheel.server.schedule.dto;

import com.bookwheel.server.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "참여 도서를 아직 등록하지 않은 ACTIVE 멤버")
public record GroupScheduleMissingBookMemberResponse(
        @Schema(description = "사용자 식별자")
        String userPK,

        @Schema(description = "화면에 표시할 사용자 닉네임")
        String nickname
) {
    public static GroupScheduleMissingBookMemberResponse from(Member member) {
        return new GroupScheduleMissingBookMemberResponse(
                member.getUser().getId(),
                member.getUser().getNickname()
        );
    }
}
