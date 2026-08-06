package com.bookwheel.server.community.dto;

public record PopularLoanBookSyncResult(
    PopularLoanBookSyncStatus status,
    int syncedCount
) {

    public static PopularLoanBookSyncResult synced(int syncedCount) {
        return new PopularLoanBookSyncResult(PopularLoanBookSyncStatus.SYNCED, syncedCount);
    }

    public static PopularLoanBookSyncResult skippedEmpty() {
        return new PopularLoanBookSyncResult(PopularLoanBookSyncStatus.SKIPPED_EMPTY, 0);
    }

    public boolean isSkippedEmpty() {
        return status == PopularLoanBookSyncStatus.SKIPPED_EMPTY;
    }
}