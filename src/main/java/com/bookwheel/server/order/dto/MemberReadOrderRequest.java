package com.bookwheel.server.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "읽기 순서 지정 요청")
public record MemberReadOrderRequest(
        @Schema(description = "랜덤 지정 여부. true면 memberIds를 보내지 않습니다.", example = "false")
        @NotNull
        Boolean isRandom,

        @Schema(description = "수동 지정 시 순서대로 전달할 멤버 ID 목록", example = "[\"member-uuid-A\", \"member-uuid-B\", \"member-uuid-C\"]")
        List<String> memberIds
) {
}
