package com.bookwheel.server.admin.controller;

import com.bookwheel.server.admin.dto.PopularLoanBookSyncResponse;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.community.dto.PopularLoanBookSyncResult;
import com.bookwheel.server.community.service.PopularLoanBookSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/books/popular-loans")
@Tag(name = "관리자(Admin) API", description = "관리자 전용 기능")
public class PopularLoanBookAdminController {

    private final PopularLoanBookSyncService popularLoanBookSyncService;
    private final Clock clock;
    private final int defaultPageSize;

    public PopularLoanBookAdminController(
        PopularLoanBookSyncService popularLoanBookSyncService,
        Clock clock,
        @Value("${naru.popular-loan.sync.page-size:1000}") int defaultPageSize
    ) {
        if (defaultPageSize <= 0) {
            throw new IllegalArgumentException("Popular loan sync page size must be greater than 0");
        }
        this.popularLoanBookSyncService = popularLoanBookSyncService;
        this.clock = clock;
        this.defaultPageSize = defaultPageSize;
    }

    @Operation(summary = "정보나루 인기대출도서 수동 적재",
        description = "관리자가 정보나루 인기대출도서 데이터를 수동으로 적재합니다. "
            + "startDate와 endDate를 생략하면 직전 달 데이터를 적재합니다.")
    @PostMapping("/sync")
    public ApiResponse<PopularLoanBookSyncResponse> syncPopularLoanBooks(
        @Parameter(description = "집계 시작일. 생략 시 직전 달 1일", example = "2026-07-01")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate startDate,

        @Parameter(description = "집계 종료일. 생략 시 직전 달 말일", example = "2026-07-31")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate endDate,

        @Parameter(description = "정보나루 조회 건수. 생략 시 서버 기본값 사용", example = "1000")
        @RequestParam(required = false)
        Integer pageSize
    ) {
        SyncPeriod period = resolvePeriod(startDate, endDate);
        int resolvedPageSize = resolvePageSize(pageSize);
        PopularLoanBookSyncResult result = popularLoanBookSyncService.syncPopularLoanBooks(
            period.startDate(),
            period.endDate(),
            resolvedPageSize
        );

        return ApiResponse.success(PopularLoanBookSyncResponse.of(
            period.startDate(),
            period.endDate(),
            resolvedPageSize,
            result
        ));
    }

    private SyncPeriod resolvePeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            LocalDate today = LocalDate.now(clock);
            return new SyncPeriod(
                today.minusMonths(1).withDayOfMonth(1),
                today.withDayOfMonth(1).minusDays(1)
            );
        }

        if (startDate == null || endDate == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        return new SyncPeriod(startDate, endDate);
    }

    private int resolvePageSize(Integer pageSize) {
        if (pageSize == null) {
            return defaultPageSize;
        }

        if (pageSize <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        return pageSize;
    }

    private record SyncPeriod(
        LocalDate startDate,
        LocalDate endDate
    ) {}
}
