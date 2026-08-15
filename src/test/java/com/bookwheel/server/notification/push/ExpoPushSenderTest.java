package com.bookwheel.server.notification.push;

import com.bookwheel.server.notification.entity.Notification;
import com.bookwheel.server.notification.enums.NotificationType;
import com.bookwheel.server.notification.service.ExpoPushReceiptService;
import com.bookwheel.server.notification.service.NotificationPreferenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.LongStream;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class ExpoPushSenderTest {

    private static final String PUSH_URL = "https://exp.host/--/api/v2/push/send";

    private MockRestServiceServer server;
    private ExpoPushSender sender;
    private NotificationPreferenceService preferenceService;
    private ExpoPushReceiptService receiptService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(PUSH_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        preferenceService = mock(NotificationPreferenceService.class);
        receiptService = mock(ExpoPushReceiptService.class);
        sender = new ExpoPushSender(
                builder.build(),
                new ObjectMapper(),
                preferenceService,
                receiptService,
                0
        );
    }

    @Test
    @DisplayName("Expo API에 제목, 본문과 이동용 data를 전송한다")
    void sendIncludesNotificationMetadataAndPayloadInData() {
        Notification notification = notification(
                42L,
                "{\"reviewId\":3,\"isbn\":\"9788954681179\",\"notificationId\":999,"
                        + "\"mail\":\"reader@example.com\",\"reasonMessage\":\"private\"}"
        );

        server.expect(requestTo(PUSH_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        [{
                          "to": "ExponentPushToken[valid_token]",
                          "title": "리뷰 공감",
                          "body": "회원님의 리뷰에 공감했어요.",
                          "sound": "default",
                          "priority": "high",
                          "data": {
                            "isbn": "9788954681179",
                            "notificationId": 42,
                            "type": "REVIEW_LIKED",
                            "deepLink": "/reviews/3"
                          }
                        }]
                        """))
                .andExpect(jsonPath("$[0].data.reviewId").doesNotExist())
                .andExpect(jsonPath("$[0].data.mail").doesNotExist())
                .andExpect(jsonPath("$[0].data.reasonMessage").doesNotExist())
                .andRespond(withSuccess("""
                        {"data":[{"status":"ok","id":"receipt-42"}]}
                        """, MediaType.APPLICATION_JSON));

        sender.send("ExponentPushToken[valid_token]", notification);

        server.verify();
        then(receiptService).should().trackAll(List.of(new ExpoPushReceiptRegistration(
                "receipt-42",
                "ExponentPushToken[valid_token]",
                42L
        )));
    }

    @Test
    @DisplayName("Expo 제한에 맞춰 100개 단위로 나누되 각 알림 ID를 유지한다")
    void sendBatchChunksMessagesByOneHundred() {
        List<PushTarget> targets = LongStream.rangeClosed(1, 101)
                .mapToObj(id -> new PushTarget(
                        "ExpoPushToken[token_" + id + "]",
                        notification(id, null)
                ))
                .toList();

        server.expect(requestTo(PUSH_URL))
                .andExpect(jsonPath("$", hasSize(100)))
                .andExpect(jsonPath("$[0].data.notificationId").value(1))
                .andExpect(jsonPath("$[99].data.notificationId").value(100))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(PUSH_URL))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].data.notificationId").value(101))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        sender.sendBatch(targets);

        server.verify();
    }

    @Test
    @DisplayName("Expo 형식이 아닌 토큰은 외부 API로 보내지 않는다")
    void sendBatchSkipsInvalidToken() {
        sender.sendBatch(List.of(new PushTarget("native-fcm-token", notification(1L, null))));

        server.verify();
    }

    @Test
    @DisplayName("Expo 공식 SDK가 허용하는 UUID 토큰에도 발송한다")
    void sendAcceptsUuidExpoPushToken() {
        String uuidToken = "123e4567-e89b-12d3-a456-426614174000";
        server.expect(requestTo(PUSH_URL))
                .andExpect(jsonPath("$[0].to").value(uuidToken))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        sender.send(uuidToken, notification(1L, null));

        server.verify();
    }

    @Test
    @DisplayName("일시적인 Expo 5xx 응답은 재시도한다")
    void sendRetriesTemporaryServerFailure() {
        server.expect(requestTo(PUSH_URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(PUSH_URL))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        sender.send("ExpoPushToken[retry_token]", notification(1L, null));

        server.verify();
    }

    @Test
    @DisplayName("한 청크가 재시도 후 실패해도 다음 청크는 계속 전송한다")
    void sendBatchContinuesAfterChunkFailure() {
        List<PushTarget> targets = LongStream.rangeClosed(1, 101)
                .mapToObj(id -> new PushTarget(
                        "ExpoPushToken[token_" + id + "]",
                        notification(id, null)
                ))
                .toList();

        for (int attempt = 0; attempt < 3; attempt++) {
            server.expect(requestTo(PUSH_URL))
                    .andExpect(jsonPath("$", hasSize(100)))
                    .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        }
        server.expect(requestTo(PUSH_URL))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].data.notificationId").value(101))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        sender.sendBatch(targets);

        server.verify();
    }

    @Test
    @DisplayName("즉시 Ticket에서 DeviceNotRegistered가 오면 토큰을 해제한다")
    void sendClearsDeviceNotRegisteredTokenFromTicket() {
        String token = "ExpoPushToken[expired_token]";
        server.expect(requestTo(PUSH_URL))
                .andRespond(withSuccess("""
                        {"data":[{
                          "status":"error",
                          "message":"not registered",
                          "details":{"error":"DeviceNotRegistered"}
                        }]}
                        """, MediaType.APPLICATION_JSON));

        sender.send(token, notification(1L, null));

        then(preferenceService).should().clearInvalidExpoPushToken(token);
    }

    private Notification notification(Long id, String payload) {
        return Notification.builder()
                .id(id)
                .recipientUserPK("recipientUserPK")
                .type(NotificationType.REVIEW_LIKED)
                .category(NotificationType.REVIEW_LIKED.getCategory())
                .title("리뷰 공감")
                .body("회원님의 리뷰에 공감했어요.")
                .deepLink("/reviews/3")
                .payload(payload)
                .build();
    }
}
