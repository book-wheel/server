package com.bookwheel.server.dashboard.dto;

import com.bookwheel.server.wheel.enums.WheelStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "내 책의 현재 진행 정보")
public record MyBookStepResponse(
        @Schema(description = "책 ID", example = "book-uuid-123")
        String bookId,

        @Schema(description = "내가 등록한 책 제목", example = "채식주의자")
        String bookTitle,

        @Schema(description = "현재 또는 시작 전 배정된 책 독자 닉네임. 배정 전이면 null입니다.", example = "홍길동", nullable = true)
        String holderNickname,

        @Schema(description = "저장된 상태값. 배정 전이면 null이고 시작 전 배정은 PLANNED입니다.", example = "PLANNED", nullable = true)
        WheelStatus status,

        @Schema(description = "오프라인 그룹의 책 위치. 온라인이거나 시작 전이면 null입니다.", example = "서울특별시", nullable = true)
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
