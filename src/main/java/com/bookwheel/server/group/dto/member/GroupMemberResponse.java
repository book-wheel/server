package com.bookwheel.server.group.dto.member;

import com.bookwheel.server.member.entity.Member;
import com.bookwheel.server.member.enums.MemberRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
public record GroupMemberResponse(
        @Schema(description = "읽기 순서 지정 API에서 사용하는 모임 멤버 식별자")
        String memberId,
        String userPK,
        String nickname,
        String profileImageUrl,
        MemberRole role,
        @Schema(description = "현재 읽기 순서. 아직 지정되지 않았으면 null", nullable = true)
        Integer readOrder,
        @Schema(description = "현재 라운드 독서 배정 정보. 현재 라운드 또는 배정이 없으면 null", nullable = true)
        GroupMemberCurrentRoundAssignmentResponse currentRoundAssignment
) {
    public static GroupMemberResponse from(
            Member member,
            String profileImageUrl,
            GroupMemberCurrentRoundAssignmentResponse currentRoundAssignment
    ) {
        return GroupMemberResponse.builder()
                .memberId(member.getMemberId())
                .userPK(member.getUser().getId())
                .nickname(member.getUser().getNickname())
                .profileImageUrl(profileImageUrl)
                .role(member.getMemberRole())
                .readOrder(member.getReadOrder())
                .currentRoundAssignment(currentRoundAssignment)
                .build();
    }
}
