package com.bookwheel.server.notification.scheduler;

import com.bookwheel.server.notification.entity.ExpoPushReceipt;
import com.bookwheel.server.notification.push.ExpoPushReceiptResult;
import com.bookwheel.server.notification.service.ExpoPushReceiptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ExpoPushReceiptScheduler {

    private static final Duration RECEIPT_READY_DELAY = Duration.ofMinutes(15);
    private static final Duration RECEIPT_RETENTION = Duration.ofHours(24);

    private final RestClient restClient;
    private final ExpoPushReceiptService receiptService;
    private final Clock clock;

    public ExpoPushReceiptScheduler(
            @Qualifier("expoPushReceiptRestClient") RestClient restClient,
            ExpoPushReceiptService receiptService,
            Clock clock
    ) {
        this.restClient = restClient;
        this.receiptService = receiptService;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${expo.push.receipt-check-interval-ms:60000}",
            initialDelayString = "${expo.push.receipt-check-initial-delay-ms:60000}"
    )
    public void checkReceipts() {
        Instant now = clock.instant();
        long expiredCount = receiptService.discardExpired(now.minus(RECEIPT_RETENTION));
        if (expiredCount > 0) {
            log.warn("Discarded expired Expo Push receipts without a result: count={}", expiredCount);
        }

        List<ExpoPushReceipt> pending = receiptService.findReady(now.minus(RECEIPT_READY_DELAY));
        if (pending.isEmpty()) {
            return;
        }

        try {
            ExpoReceiptResponse response = restClient.post()
                    .body(new ExpoReceiptRequest(pending.stream().map(ExpoPushReceipt::getReceiptId).toList()))
                    .retrieve()
                    .body(ExpoReceiptResponse.class);

            if (response == null) {
                log.warn("Expo Push receipt response body is empty: requested={}", pending.size());
                return;
            }
            if (response.errors() != null) {
                for (ExpoReceiptRequestError error : response.errors()) {
                    log.warn("Expo Push receipt request error: code={}, message={}", error.code(), error.message());
                }
            }
            if (response.data() == null || response.data().isEmpty()) {
                return;
            }

            Map<String, ExpoPushReceiptResult> results = new LinkedHashMap<>();
            response.data().forEach((receiptId, receipt) -> results.put(
                    receiptId,
                    new ExpoPushReceiptResult(receipt.status(), receipt.errorCode(), receipt.message())
            ));
            receiptService.complete(results);
        } catch (Exception exception) {
            // Receipt는 24시간 보관되므로 일시 오류는 다음 스케줄에서 다시 시도한다.
            log.warn("Expo Push receipt check failed: requested={}, reason={}",
                    pending.size(), exception.getMessage());
        }
    }

    private record ExpoReceiptRequest(List<String> ids) {
    }

    private record ExpoReceiptResponse(
            Map<String, ExpoReceipt> data,
            List<ExpoReceiptRequestError> errors
    ) {
    }

    private record ExpoReceipt(
            String status,
            String message,
            Map<String, Object> details
    ) {
        private String errorCode() {
            Object error = details == null ? null : details.get("error");
            return error == null ? null : error.toString();
        }
    }

    private record ExpoReceiptRequestError(String code, String message) {
    }
}
