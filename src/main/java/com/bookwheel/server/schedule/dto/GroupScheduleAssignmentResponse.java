package com.bookwheel.server.schedule.dto;

import com.bookwheel.server.schedule.entity.Round;
import com.bookwheel.server.wheel.entity.WheelState;
import com.bookwheel.server.wheel.enums.WheelStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "내 독서 일정과 저장된 책 배정 정보")
public record GroupScheduleAssignmentResponse(
        @Schema(description = "라운드 번호", example = "1")
        int roundNumber,

        @Schema(description = "라운드 시작일", example = "2026-07-13")
        LocalDate startDate,

        @Schema(description = "라운드 종료일", example = "2026-07-15")
        LocalDate endDate,

        @Schema(description = "저장된 책바퀴 상태 ID", example = "wheel-uuid-111", nullable = true)
        String wheelStateId,

        @Schema(description = "저장된 책바퀴 상태", example = "PLANNED", nullable = true)
        WheelStatus wheelStatus,

        @Schema(description = "배정된 책 ID", example = "book-uuid-123", nullable = true)
        String bookId,

        @Schema(description = "배정된 책 제목", example = "소년이 온다", nullable = true)
        String bookTitle,

        @Schema(description = "배정된 책 표지 이미지", nullable = true)
        String coverImage,

        @Schema(description = "책을 보낼 사람 닉네임", example = "책벌레", nullable = true)
        String senderNickname
) {
    public static GroupScheduleAssignmentResponse withoutAssignment(
            int roundNumber,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return new GroupScheduleAssignmentResponse(
                roundNumber, startDate, endDate,
                null, null, null, null, null, null
        );
    }

    public static GroupScheduleAssignmentResponse of(Round round, WheelState wheelState, String senderNickname) {
        if (wheelState == null) {
            return withoutAssignment(round.getRoundNumber(), round.getStartDate(), round.getEndDate());
        }

        return new GroupScheduleAssignmentResponse(
                round.getRoundNumber(),
                round.getStartDate(),
                round.getEndDate(),
                wheelState.getWheelStateId(),
                wheelState.getWheelState(),
                wheelState.getOwnBook().getBook().getBookId(),
                wheelState.getOwnBook().getBook().getTitle(),
                wheelState.getOwnBook().getBook().getCoverImage(),
                senderNickname
        );
    }
}
