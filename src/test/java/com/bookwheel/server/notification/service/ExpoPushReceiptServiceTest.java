package com.bookwheel.server.notification.service;

import com.bookwheel.server.notification.entity.ExpoPushReceipt;
import com.bookwheel.server.notification.push.ExpoPushReceiptRegistration;
import com.bookwheel.server.notification.push.ExpoPushReceiptResult;
import com.bookwheel.server.notification.repository.ExpoPushReceiptRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ExpoPushReceiptServiceTest {

    @Mock
    private ExpoPushReceiptRepository receiptRepository;

    @Mock
    private NotificationPreferenceService preferenceService;

    @InjectMocks
    private ExpoPushReceiptService receiptService;

    @Test
    @DisplayName("성공 Ticket을 나중에 조회할 Receipt로 저장한다")
    @SuppressWarnings("unchecked")
    void trackAllPersistsReceiptMapping() {
        receiptService.trackAll(List.of(new ExpoPushReceiptRegistration(
                "receipt-1",
                "ExpoPushToken[token]",
                41L
        )));

        ArgumentCaptor<List<ExpoPushReceipt>> captor = ArgumentCaptor.forClass(List.class);
        then(receiptRepository).should().saveAll(captor.capture());
        ExpoPushReceipt receipt = captor.getValue().get(0);
        assertThat(receipt.getReceiptId()).isEqualTo("receipt-1");
        assertThat(receipt.getExpoPushToken()).isEqualTo("ExpoPushToken[token]");
        assertThat(receipt.getNotificationId()).isEqualTo(41L);
    }

    @Test
    @DisplayName("Receipt의 DeviceNotRegistered 결과로 만료 토큰을 제거한다")
    void completeClearsInvalidTokenAndDeletesReceipt() {
        ExpoPushReceipt receipt = ExpoPushReceipt.pending(
                "receipt-1",
                "ExpoPushToken[expired]",
                41L
        );
        given(receiptRepository.findAllById(any())).willReturn(List.of(receipt));

        receiptService.complete(Map.of(
                "receipt-1",
                new ExpoPushReceiptResult("error", "DeviceNotRegistered", "not registered")
        ));

        then(preferenceService).should().clearInvalidExpoPushToken("ExpoPushToken[expired]");
        then(receiptRepository).should().deleteAllInBatch(List.of(receipt));
    }
}
