package com.bookwheel.server.group.dto;

import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record MemberRequestResponse(
        String memberId,
        String userPk,
        String nickname,
        String joinMent,
        LocalDateTime requestDate,
        MemberStatus status
) {
    public static MemberRequestResponse from(Member member) {
        return MemberRequestResponse.builder()
                .memberId(member.getMemberId())
                .userPk(member.getUser().getId())
                .nickname(member.getUser().getNickname())
                .joinMent(member.getJoinMent())
                .requestDate(member.getRequestDate())
                .status(member.getMemberStatus())
                .build();
    }
}

