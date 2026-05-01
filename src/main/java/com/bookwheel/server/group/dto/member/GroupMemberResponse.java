package com.bookwheel.server.group.dto.member;

import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberRole;
import lombok.Builder;

@Builder
public record GroupMemberResponse(
        String userPK,
        String nickname,
        String profileImageUrl,
        MemberRole role
) {
    public static GroupMemberResponse from(Member member, String profileImageUrl) {
        return GroupMemberResponse.builder()
                .userPK(member.getUser().getId())
                .nickname(member.getUser().getNickname())
                .profileImageUrl(profileImageUrl)
                .role(member.getMemberRole())
                .build();
    }
}
