package com.bookwheel.server.dashboard.dto;

import com.bookwheel.server.wheel.enums.WheelStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "내가 현재 읽는 단계 정보")
public record MyStepResponse(
        @Schema(description = "저장된 책바퀴 상태 ID. 시작 전 배정도 실제 ID를 반환합니다.", example = "wheel-uuid-111", nullable = true)
        String wheelStateId,

        @Schema(description = "책 ID", example = "book-uuid-123")
        String bookId,

        @Schema(description = "저장된 상태값. 시작 전 배정은 PLANNED입니다.", example = "PLANNED")
        WheelStatus status,

        @Schema(description = "현재 읽는 책 제목", example = "소년이 온다")
        String bookTitle,

        @Schema(description = "표지 이미지 URL", example = "https://image.aladin.co.kr/...")
        String coverImage,

        @Schema(description = "직전 회차에서 책을 전달한 멤버 닉네임. 1회차는 원래 책 소유자", example = "직전 전달자", nullable = true, types = {"string", "null"})
        String senderNickname,

        @Schema(description = "책의 원래 소유자 닉네임", example = "책 주인", nullable = true, types = {"string", "null"})
        String ownerNickname
) {
    public static MyStepResponse of(
            String wheelStateId,
            String bookId,
            WheelStatus status,
            String bookTitle,
            String coverImage,
            String senderNickname,
            String ownerNickname
    ) {
        return MyStepResponse.builder()
                .wheelStateId(wheelStateId)
                .bookId(bookId)
                .status(status)
                .bookTitle(bookTitle)
                .coverImage(coverImage)
                .senderNickname(senderNickname)
                .ownerNickname(ownerNickname)
                .build();
    }
}
