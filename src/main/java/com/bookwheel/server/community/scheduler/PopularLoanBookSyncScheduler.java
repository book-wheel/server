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
            throw new IllegalArgumentException("인기대출도서 적재 조회 건수는 1 이상이어야 합니다. - pageSize: " + pageSize);
        }
        this.popularLoanBookSyncService = popularLoanBookSyncService;
        this.clock = clock;
        this.pageSize = pageSize;
    }

    /**
     * 직전 달 인기대출도서를 적재한다.
     *
     * 정보나루는 전월 집계를 1일에 바로 공개하지 않고 며칠 늦게 올리는 경우가 있다.
     * 1일에 한 번만 시도하면 그날 데이터가 비어 있을 때 다음 시도가 한 달 뒤가 되어
     * 그동안 두 달 전 스냅샷으로 추천이 나가므로, 매월 1~5일 새벽 4시에 재시도한다.
     * 추천 순환에 필요한 후보가 모두 확보된 날에는 외부 API를 호출하지 않고 건너뛴다.
     */
    @Scheduled(cron = "${naru.popular-loan.sync.cron:0 0 4 1-5 * *}", zone = "Asia/Seoul")
    public void syncPreviousMonthPopularLoanBooks() {
        LocalDate today = LocalDate.now(clock);
        LocalDate startDate = today.minusMonths(1).withDayOfMonth(1);
        LocalDate endDate = today.withDayOfMonth(1).minusDays(1);

        if (popularLoanBookSyncService.hasEnoughCandidates(startDate, endDate)) {
            log.info(
                "직전 달 인기대출도서 추천 후보가 이미 확보되어 건너뜁니다 - startDate: {}, endDate: {}",
                startDate,
                endDate
            );
            return;
        }

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
                    "직전 달 인기대출도서 조회 결과가 비어 적재를 건너뜁니다 - startDate: {}, endDate: {}",
                    startDate,
                    endDate
                );
                return;
            }

            log.info(
                "직전 달 인기대출도서 적재 완료 - startDate: {}, endDate: {}, count: {}",
                startDate,
                endDate,
                result.syncedCount()
            );
        } catch (RuntimeException exception) {
            log.error(
                "직전 달 인기대출도서 적재 실패 - startDate: {}, endDate: {}, error: {}",
                startDate,
                endDate,
                exception.getMessage()
            );
        }
    }
}
