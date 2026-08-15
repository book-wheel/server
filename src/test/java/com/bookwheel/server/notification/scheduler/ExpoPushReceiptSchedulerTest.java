package com.bookwheel.server.notification.scheduler;

import com.bookwheel.server.notification.entity.ExpoPushReceipt;
import com.bookwheel.server.notification.push.ExpoPushReceiptResult;
import com.bookwheel.server.notification.service.ExpoPushReceiptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ExpoPushReceiptSchedulerTest {

    private static final String RECEIPTS_URL = "https://exp.host/--/api/v2/push/getReceipts";

    private MockRestServiceServer server;
    private ExpoPushReceiptService receiptService;
    private ExpoPushReceiptScheduler scheduler;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(RECEIPTS_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        receiptService = mock(ExpoPushReceiptService.class);
        scheduler = new ExpoPushReceiptScheduler(
                builder.build(),
                receiptService,
                Clock.fixed(Instant.parse("2026-08-14T03:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("15분 지난 Ticket의 Receipt를 조회해 처리 서비스에 전달한다")
    @SuppressWarnings("unchecked")
    void checkReceiptsFetchesAndCompletesReadyReceipts() {
        given(receiptService.findReady(any())).willReturn(List.of(ExpoPushReceipt.pending(
                "receipt-1",
                "ExpoPushToken[token]",
                41L
        )));
        server.expect(requestTo(RECEIPTS_URL))
                .andExpect(content().json("{\"ids\":[\"receipt-1\"]}"))
                .andRespond(withSuccess("""
                        {"data":{"receipt-1":{
                          "status":"error",
                          "message":"not registered",
                          "details":{"error":"DeviceNotRegistered"}
                        }}}
                        """, MediaType.APPLICATION_JSON));

        scheduler.checkReceipts();

        ArgumentCaptor<Map<String, ExpoPushReceiptResult>> captor = ArgumentCaptor.forClass(Map.class);
        then(receiptService).should().complete(captor.capture());
        assertThat(captor.getValue()).containsEntry(
                "receipt-1",
                new ExpoPushReceiptResult("error", "DeviceNotRegistered", "not registered")
        );
        server.verify();
    }
}
