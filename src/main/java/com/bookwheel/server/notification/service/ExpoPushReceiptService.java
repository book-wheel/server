package com.bookwheel.server.notification.service;

import com.bookwheel.server.notification.entity.ExpoPushReceipt;
import com.bookwheel.server.notification.push.ExpoPushReceiptRegistration;
import com.bookwheel.server.notification.push.ExpoPushReceiptResult;
import com.bookwheel.server.notification.repository.ExpoPushReceiptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpoPushReceiptService {

    private static final String DEVICE_NOT_REGISTERED = "DeviceNotRegistered";

    private final ExpoPushReceiptRepository receiptRepository;
    private final NotificationPreferenceService preferenceService;

    @Transactional
    public void trackAll(List<ExpoPushReceiptRegistration> registrations) {
        if (registrations == null || registrations.isEmpty()) {
            return;
        }
        receiptRepository.saveAll(registrations.stream()
                .map(registration -> ExpoPushReceipt.pending(
                        registration.receiptId(),
                        registration.expoPushToken(),
                        registration.notificationId()
                ))
                .toList());
    }

    public List<ExpoPushReceipt> findReady(Instant readyBefore) {
        return receiptRepository.findTop1000ByCreatedAtBeforeOrderByCreatedAtAsc(readyBefore);
    }

    @Transactional
    public void complete(Map<String, ExpoPushReceiptResult> results) {
        if (results == null || results.isEmpty()) {
            return;
        }

        List<ExpoPushReceipt> completed = receiptRepository.findAllById(results.keySet());
        for (ExpoPushReceipt receipt : completed) {
            ExpoPushReceiptResult result = results.get(receipt.getReceiptId());
            if (result == null) {
                continue;
            }
            if (!"ok".equals(result.status())) {
                log.warn("Expo Push receipt error: notificationId={}, error={}, message={}",
                        receipt.getNotificationId(), result.errorCode(), result.message());
            }
            if (DEVICE_NOT_REGISTERED.equals(result.errorCode())) {
                preferenceService.clearInvalidExpoPushToken(receipt.getExpoPushToken());
            }
        }
        receiptRepository.deleteAllInBatch(completed);
    }

    @Transactional
    public long discardExpired(Instant expiredBefore) {
        return receiptRepository.deleteByCreatedAtBefore(expiredBefore);
    }
}
