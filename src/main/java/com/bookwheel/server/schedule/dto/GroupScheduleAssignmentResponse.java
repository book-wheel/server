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

        @Schema(description = "현재 모집·진행 상태에서 실제 실행 대상으로 확정된 라운드인지 여부")
        boolean executable,

        @Schema(description = "저장된 책바퀴 상태 ID", example = "wheel-uuid-111", nullable = true)
        String wheelStateId,

        @Schema(description = "저장된 책바퀴 상태", example = "PLANNED", nullable = true)
        WheelStatus wheelStatus,

        @Schema(description = "모임에 등록된 도서 식별자. 도서별 완독 히스토리 조회 시 사용", example = "own-book-uuid-123", nullable = true)
        String ownBookId,

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
            LocalDate endDate,
            boolean executable
    ) {
        return new GroupScheduleAssignmentResponse(
                roundNumber, startDate, endDate, executable,
                null, null, null, null, null, null, null
        );
    }

    public static GroupScheduleAssignmentResponse of(
            Round round,
            WheelState wheelState,
            String senderNickname,
            boolean executable
    ) {
        if (wheelState == null) {
            return withoutAssignment(
                    round.getRoundNumber(),
                    round.getStartDate(),
                    round.getEndDate(),
                    executable
            );
        }

        return new GroupScheduleAssignmentResponse(
                round.getRoundNumber(),
                round.getStartDate(),
                round.getEndDate(),
                executable,
                wheelState.getWheelStateId(),
                wheelState.getWheelState(),
                wheelState.getOwnBook().getOwnBookId(),
                wheelState.getOwnBook().getBook().getBookId(),
                wheelState.getOwnBook().getBook().getTitle(),
                wheelState.getOwnBook().getBook().getCoverImage(),
                senderNickname
        );
    }
}
