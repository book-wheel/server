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
    @DisplayName("Scheduler skips the external call when the period is already synced")
    void syncPreviousMonthPopularLoanBooks_SkipsWhenAlreadySynced() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-03T00:00:00Z"), SEOUL_ZONE);
        PopularLoanBookSyncScheduler scheduler = new PopularLoanBookSyncScheduler(
            popularLoanBookSyncService,
            clock,
            PAGE_SIZE
        );
        given(popularLoanBookSyncService.isAlreadySynced(
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
    @DisplayName("Scheduler page size must be greater than zero")
    void constructor_ThrowsWhenPageSizeIsInvalid() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), SEOUL_ZONE);

        assertThatThrownBy(() -> new PopularLoanBookSyncScheduler(popularLoanBookSyncService, clock, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
