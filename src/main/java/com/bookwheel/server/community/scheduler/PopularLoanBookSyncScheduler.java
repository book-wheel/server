package com.bookwheel.server.community.scheduler;

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
            int syncedCount = popularLoanBookSyncService.syncPopularLoanBooks(startDate, endDate, pageSize);
            log.info(
                "Previous month popular loan book sync finished - startDate: {}, endDate: {}, count: {}",
                startDate,
                endDate,
                syncedCount
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
