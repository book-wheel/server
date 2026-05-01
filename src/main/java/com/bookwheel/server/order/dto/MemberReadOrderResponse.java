package com.bookwheel.server.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "읽기 순서 지정 응답 항목")
public record MemberReadOrderResponse(
        @Schema(description = "읽기 순번(1부터 시작)", example = "1")
        int order,

        @Schema(description = "멤버 고유 ID", example = "member-uuid-A")
        String memberId,

        @Schema(description = "멤버 닉네임", example = "책벌레")
        String nickname,

        @Schema(description = "프로필 이미지 URL", example = "http://...")
        String profileImageKey
) {
    public static MemberReadOrderResponse of(int order, String memberId, String nickname, String profileImage) {
        return MemberReadOrderResponse.builder()
                .order(order)
                .memberId(memberId)
                .nickname(nickname)
                .profileImageKey(profileImage)
                .build();
    }
}
