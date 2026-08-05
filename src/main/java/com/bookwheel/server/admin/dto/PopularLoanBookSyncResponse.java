package com.bookwheel.server.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "정보나루 인기대출도서 수동 적재 결과")
public record PopularLoanBookSyncResponse(

    @Schema(description = "데이터 출처", example = "DATA4LIBRARY")
    String source,

    @Schema(description = "데이터 출처 표시명", example = "도서관 정보나루 인기대출도서")
    String sourceName,

    @Schema(description = "집계 시작일", example = "2026-07-01")
    LocalDate startDate,

    @Schema(description = "집계 종료일", example = "2026-07-31")
    LocalDate endDate,

    @Schema(description = "정보나루 조회 건수", example = "1000")
    int pageSize,

    @Schema(description = "DB에 저장한 도서 수", example = "1000")
    int syncedCount
) {

    public static PopularLoanBookSyncResponse of(
        LocalDate startDate,
        LocalDate endDate,
        int pageSize,
        int syncedCount
    ) {
        return new PopularLoanBookSyncResponse(
            "DATA4LIBRARY",
            "도서관 정보나루 인기대출도서",
            startDate,
            endDate,
            pageSize,
            syncedCount
        );
    }
}
