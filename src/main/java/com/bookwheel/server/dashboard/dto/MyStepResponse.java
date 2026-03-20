package com.bookwheel.server.dashboard.dto;

import com.bookwheel.server.wheel.enums.WheelStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "내가 현재 읽는 단계 정보")
public record MyStepResponse(
        @Schema(description = "휠 상태 ID", example = "wheel-uuid-111")
        String wheelStateId,

        @Schema(description = "책 ID", example = "book-uuid-123")
        String bookId,

        @Schema(description = "상태값", example = "READING")
        WheelStatus status,

        @Schema(description = "현재 읽는 책 제목", example = "소년이 온다")
        String bookTitle,

        @Schema(description = "표지 이미지 URL", example = "https://image.aladin.co.kr/...")
        String coverImage,

        @Schema(description = "보낸 사람 닉네임", example = "책벌레")
        String senderNickname
) {
    public static MyStepResponse of(
            String wheelStateId,
            String bookId,
            WheelStatus status,
            String bookTitle,
            String coverImage,
            String senderNickname
    ) {
        return MyStepResponse.builder()
                .wheelStateId(wheelStateId)
                .bookId(bookId)
                .status(status)
                .bookTitle(bookTitle)
                .coverImage(coverImage)
                .senderNickname(senderNickname)
                .build();
    }
}
