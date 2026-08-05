package com.bookwheel.server.community.scheduler;

import com.bookwheel.server.community.dto.PopularLoanBookSyncResult;
import com.bookwheel.server.community.service.PopularLoanBookSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Slf4j
@Component
public class PopularLoanBookSyncScheduler {

    private final PopularLoanBookSyncService popularLoanBookSyncService;
    private final Clock clock;
    private final int pageSize;

    public PopularLoanBookSyncScheduler(
        PopularLoanBookSyncService popularLoanBookSyncService,
        Clock clock,
        @Value("${naru.popular-loan.sync.page-size:1000}") int pageSize
    ) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("Popular loan sync page size must be greater than 0");
        }
        this.popularLoanBookSyncService = popularLoanBookSyncService;
        this.clock = clock;
        this.pageSize = pageSize;
    }

    @Scheduled(cron = "${naru.popular-loan.sync.cron:0 0 4 1 * *}", zone = "Asia/Seoul")
    public void syncPreviousMonthPopularLoanBooks() {
        LocalDate today = LocalDate.now(clock);
        LocalDate startDate = today.minusMonths(1).withDayOfMonth(1);
        LocalDate endDate = today.withDayOfMonth(1).minusDays(1);

        try {
            PopularLoanBookSyncResult result = popularLoanBookSyncService.syncPopularLoanBooks(
                startDate,
                endDate,
                pageSize
            );

            // 적재를 건너뛴 경우는 정상 완료와 구분해 기록한다. 직전 달 데이터가 비어 있는 것은
            // 장애는 아니지만 확인이 필요한 상황이다.
            if (result.isSkippedEmpty()) {
                log.warn(
                    "Previous month popular loan book sync skipped because fetched data is empty"
                        + " - startDate: {}, endDate: {}",
                    startDate,
                    endDate
                );
                return;
            }

            log.info(
                "Previous month popular loan book sync finished - startDate: {}, endDate: {}, count: {}",
                startDate,
                endDate,
                result.syncedCount()
            );
        } catch (RuntimeException exception) {
            log.error(
                "Previous month popular loan book sync failed - startDate: {}, endDate: {}, error: {}",
                startDate,
                endDate,
                exception.getMessage()
            );
        }
    }
}
