package com.bookwheel.server.community.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.dto.PopularLoanBookSyncResult;
import com.bookwheel.server.community.service.PopularLoanBookSyncService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PopularLoanBookSyncSchedulerTest {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final int PAGE_SIZE = 1000;

    @Mock
    private PopularLoanBookSyncService popularLoanBookSyncService;

    @Test
    @DisplayName("Scheduler syncs the previous month popular loan period")
    void syncPreviousMonthPopularLoanBooks_SyncsPreviousMonth() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), SEOUL_ZONE);
        PopularLoanBookSyncScheduler scheduler = new PopularLoanBookSyncScheduler(
            popularLoanBookSyncService,
            clock,
            PAGE_SIZE
        );

        given(popularLoanBookSyncService.syncPopularLoanBooks(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            PAGE_SIZE
        )).willReturn(PopularLoanBookSyncResult.synced(1000));

        scheduler.syncPreviousMonthPopularLoanBooks();

        then(popularLoanBookSyncService).should().syncPopularLoanBooks(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            PAGE_SIZE
        );
    }

    @Test
    @DisplayName("Scheduler completes without failure when the sync is skipped for empty data")
    void syncPreviousMonthPopularLoanBooks_HandlesSkippedEmptyResult() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), SEOUL_ZONE);
        PopularLoanBookSyncScheduler scheduler = new PopularLoanBookSyncScheduler(
            popularLoanBookSyncService,
            clock,
            PAGE_SIZE
        );
        given(popularLoanBookSyncService.syncPopularLoanBooks(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            PAGE_SIZE
        )).willReturn(PopularLoanBookSyncResult.skippedEmpty());

        assertThatCode(scheduler::syncPreviousMonthPopularLoanBooks).doesNotThrowAnyException();

        then(popularLoanBookSyncService).should().syncPopularLoanBooks(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            PAGE_SIZE
        );
    }

    @Test
    @DisplayName("Scheduler does not propagate sync failures")
    void syncPreviousMonthPopularLoanBooks_DoesNotPropagateFailure() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), SEOUL_ZONE);
        PopularLoanBookSyncScheduler scheduler = new PopularLoanBookSyncScheduler(
            popularLoanBookSyncService,
            clock,
            PAGE_SIZE
        );
        given(popularLoanBookSyncService.syncPopularLoanBooks(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            PAGE_SIZE
        )).willThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

        assertThatCode(scheduler::syncPreviousMonthPopularLoanBooks).doesNotThrowAnyException();

        then(popularLoanBookSyncService).should().syncPopularLoanBooks(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            PAGE_SIZE
        );
    }

    @Test
    @DisplayName("Scheduler skips the external call when the recommendation candidates are already synced")
    void syncPreviousMonthPopularLoanBooks_SkipsWhenCandidatesAreSecured() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-03T00:00:00Z"), SEOUL_ZONE);
        PopularLoanBookSyncScheduler scheduler = new PopularLoanBookSyncScheduler(
            popularLoanBookSyncService,
            clock,
            PAGE_SIZE
        );
        given(popularLoanBookSyncService.hasEnoughCandidates(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31)
        )).willReturn(true);

        scheduler.syncPreviousMonthPopularLoanBooks();

        then(popularLoanBookSyncService).should(never()).syncPopularLoanBooks(
            any(LocalDate.class),
            any(LocalDate.class),
            anyInt()
        );
    }

    @Test
    @DisplayName("Scheduler retries the sync when only a few books are stored for the period")
    void syncPreviousMonthPopularLoanBooks_RetriesWhenCandidatesAreNotSecured() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-03T00:00:00Z"), SEOUL_ZONE);
        PopularLoanBookSyncScheduler scheduler = new PopularLoanBookSyncScheduler(
            popularLoanBookSyncService,
            clock,
            PAGE_SIZE
        );
        // 소량만 적재된 기간은 "적재 완료"로 보지 않고 남은 날에 다시 시도해야 한다.
        given(popularLoanBookSyncService.hasEnoughCandidates(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31)
        )).willReturn(false);
        given(popularLoanBookSyncService.syncPopularLoanBooks(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            PAGE_SIZE
        )).willReturn(PopularLoanBookSyncResult.synced(1000));

        scheduler.syncPreviousMonthPopularLoanBooks();

        then(popularLoanBookSyncService).should().syncPopularLoanBooks(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            PAGE_SIZE
        );
    }

    @Test
    @DisplayName("Scheduler page size must be greater than zero")
    void constructor_ThrowsWhenPageSizeIsInvalid() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), SEOUL_ZONE);

        assertThatThrownBy(() -> new PopularLoanBookSyncScheduler(popularLoanBookSyncService, clock, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
