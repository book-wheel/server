package com.bookwheel.server.dashboard.dto;

import com.bookwheel.server.wheel.enums.WheelStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "내 책의 현재 진행 정보")
public record MyBookStepResponse(
        @Schema(description = "책 ID", example = "book-uuid-123")
        String bookId,

        @Schema(description = "내 책 제목", example = "채식주의자")
        String bookTitle,

        @Schema(description = "현재 보유자 닉네임", example = "홍길동")
        String holderNickname,

        @Schema(description = "상태값", example = "READING")
        WheelStatus status,

        @Schema(description = "책 위치 정보 (없으면 null)", example = "서울시 마포구", nullable = true)
        String location
) {
    public static MyBookStepResponse of(
            String bookId,
            String bookTitle,
            String holderNickname,
            WheelStatus status,
            String location
    ) {
        return MyBookStepResponse.builder()
                .bookId(bookId)
                .bookTitle(bookTitle)
                .holderNickname(holderNickname)
                .status(status)
                .location(location)
                .build();
    }
}
