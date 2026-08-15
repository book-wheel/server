package com.bookwheel.server.group.dto.member;

import com.bookwheel.server.book.entity.Book;
import com.bookwheel.server.wheel.entity.WheelState;
import com.bookwheel.server.wheel.enums.WheelStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "멤버의 현재 라운드 독서 배정 정보")
public record GroupMemberCurrentRoundAssignmentResponse(
        @Schema(description = "책바퀴 상태 ID", example = "wheel-uuid-123")
        String wheelStateId,

        @Schema(description = "배정된 책 ID", example = "book-uuid-123")
        String bookId,

        @Schema(description = "배정된 책 제목", example = "소년이 온다")
        String bookTitle,

        @Schema(description = "배정된 책 표지 이미지 URL", example = "https://image.aladin.co.kr/...")
        String coverImage,

        @Schema(
                description = "현재 라운드 독서 상태",
                example = "READY",
                allowableValues = {"PLANNED", "WAITING", "READY", "READING", "COMPLETED", "UNFINISHED"}
        )
        WheelStatus readingStatus
) {
    public static GroupMemberCurrentRoundAssignmentResponse from(WheelState wheelState) {
        Book book = wheelState.getOwnBook().getBook();

        return new GroupMemberCurrentRoundAssignmentResponse(
                wheelState.getWheelStateId(),
                book.getBookId(),
                book.getTitle(),
                book.getCoverImage(),
                wheelState.getWheelState()
        );
    }
}
