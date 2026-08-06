package com.bookwheel.server.admin.dto;

import com.bookwheel.server.community.dto.PopularLoanBookSyncResult;
import com.bookwheel.server.community.dto.PopularLoanBookSyncStatus;
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

    @Schema(
        description = "적재 결과 상태. SYNCED는 스냅샷을 새로 적재했음을, "
            + "SKIPPED_EMPTY는 조회 결과가 비어 있어 적재를 건너뛰고 기존 스냅샷을 유지했음을 의미한다.",
        example = "SYNCED"
    )
    PopularLoanBookSyncStatus status,

    @Schema(description = "DB에 저장한 도서 수", example = "1000")
    int syncedCount
) {

    public static PopularLoanBookSyncResponse of(
        LocalDate startDate,
        LocalDate endDate,
        int pageSize,
        PopularLoanBookSyncResult result
    ) {
        return new PopularLoanBookSyncResponse(
            "DATA4LIBRARY",
            "도서관 정보나루 인기대출도서",
            startDate,
            endDate,
            pageSize,
            result.status(),
            result.syncedCount()
        );
    }
}
