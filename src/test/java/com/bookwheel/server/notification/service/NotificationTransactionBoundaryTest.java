package com.bookwheel.server.notification.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTransactionBoundaryTest {

    @Test
    @DisplayName("푸시 대상 재조회와 Receipt 저장, 만료 토큰 해제는 독립 트랜잭션을 사용한다")
    void followUpDatabaseWritesUseIndependentTransactions() throws NoSuchMethodException {
        Transactional targetResolution = NotificationPushTargetResolver.class
                .getMethod("resolve", List.class)
                .getAnnotation(Transactional.class);
        Transactional receiptTracking = ExpoPushReceiptService.class
                .getMethod("trackAll", List.class)
                .getAnnotation(Transactional.class);
        Transactional invalidTokenClear = NotificationPreferenceService.class
                .getMethod("clearInvalidExpoPushToken", String.class)
                .getAnnotation(Transactional.class);

        assertThat(targetResolution.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(targetResolution.readOnly()).isTrue();
        assertThat(receiptTracking.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(invalidTokenClear.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
