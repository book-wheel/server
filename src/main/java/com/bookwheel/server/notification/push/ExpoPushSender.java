package com.bookwheel.server.notification.push;

import com.bookwheel.server.notification.entity.Notification;
import com.bookwheel.server.notification.dto.NotificationDataResponse;
import com.bookwheel.server.notification.service.ExpoPushReceiptService;
import com.bookwheel.server.notification.service.NotificationPreferenceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sends persisted notifications through the Expo Push Service HTTP API.
 */
@Slf4j
@Component
public class ExpoPushSender implements PushSender {

    private static final int EXPO_MAX_BATCH_SIZE = 100;
    private static final int MAX_SEND_ATTEMPTS = 3;
    private static final String DEVICE_NOT_REGISTERED = "DeviceNotRegistered";
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final NotificationPreferenceService preferenceService;
    private final ExpoPushReceiptService receiptService;
    private final long retryInitialDelayMillis;

    public ExpoPushSender(
            @Qualifier("expoPushRestClient") RestClient restClient,
            ObjectMapper objectMapper,
            NotificationPreferenceService preferenceService,
            ExpoPushReceiptService receiptService,
            @Value("${expo.push.retry-initial-delay-ms:500}") long retryInitialDelayMillis
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.preferenceService = preferenceService;
        this.receiptService = receiptService;
        this.retryInitialDelayMillis = Math.max(0, retryInitialDelayMillis);
    }

    @Override
    public void send(String expoPushToken, Notification notification) {
        sendBatch(List.of(new PushTarget(expoPushToken, notification)));
    }

    @Override
    public void sendBatch(List<PushTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return;
        }

        List<PushTarget> validTargets = targets.stream()
                .filter(target -> target != null
                        && target.notification() != null
                        && ExpoPushToken.isValid(target.expoPushToken()))
                .toList();

        if (validTargets.size() != targets.size()) {
            log.warn("Skipped invalid Expo Push targets: requested={}, valid={}",
                    targets.size(), validTargets.size());
        }

        for (int start = 0; start < validTargets.size(); start += EXPO_MAX_BATCH_SIZE) {
            int end = Math.min(start + EXPO_MAX_BATCH_SIZE, validTargets.size());
            List<PushTarget> chunk = validTargets.subList(start, end);
            try {
                sendChunkWithRetry(chunk);
            } catch (RuntimeException exception) {
                log.warn("Expo Push chunk failed after retries: start={}, count={}, reason={}",
                        start, chunk.size(), exception.getMessage());
            }
        }
    }

    private void sendChunkWithRetry(List<PushTarget> targets) {
        for (int attempt = 1; attempt <= MAX_SEND_ATTEMPTS; attempt++) {
            try {
                sendChunk(targets);
                return;
            } catch (RuntimeException exception) {
                if (!isRetryable(exception) || attempt == MAX_SEND_ATTEMPTS) {
                    throw exception;
                }
                long delayMillis = retryInitialDelayMillis * (1L << (attempt - 1));
                log.warn("Retrying Expo Push chunk: attempt={}, count={}, delayMs={}, reason={}",
                        attempt + 1, targets.size(), delayMillis, exception.getMessage());
                sleep(delayMillis);
            }
        }
    }

    private void sendChunk(List<PushTarget> targets) {
        List<ExpoPushMessage> messages = targets.stream()
                .map(this::toMessage)
                .toList();

        ExpoPushResponse response = restClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(messages)
                .retrieve()
                .body(ExpoPushResponse.class);

        if (response == null) {
            throw new IllegalStateException("Expo Push response body is empty.");
        }

        if (response.errors() != null) {
            for (ExpoRequestError error : response.errors()) {
                log.warn("Expo Push request error: code={}, message={}", error.code(), error.message());
            }
            boolean rateLimited = response.errors().stream()
                    .anyMatch(error -> "TOO_MANY_REQUESTS".equals(error.code()));
            if (rateLimited && (response.data() == null || response.data().isEmpty())) {
                throw new RetryableExpoPushException("Expo Push request was rate limited.");
            }
        }

        if (response.data() == null) {
            return;
        }

        if (response.data().size() != targets.size()) {
            log.warn("Expo Push ticket count mismatch: requested={}, received={}",
                    targets.size(), response.data().size());
        }

        List<ExpoPushReceiptRegistration> registrations = new ArrayList<>();
        for (int index = 0; index < response.data().size(); index++) {
            ExpoPushTicket ticket = response.data().get(index);
            PushTarget target = index < targets.size() ? targets.get(index) : null;
            Long notificationId = target == null ? null : target.notification().getId();
            if ("error".equals(ticket.status())) {
                log.warn("Expo Push ticket error: notificationId={}, error={}, message={}",
                        notificationId, ticket.errorCode(), ticket.message());
                if (target != null && DEVICE_NOT_REGISTERED.equals(ticket.errorCode())) {
                    preferenceService.clearInvalidExpoPushToken(target.expoPushToken());
                }
            } else if ("ok".equals(ticket.status()) && ticket.id() != null && target != null) {
                registrations.add(new ExpoPushReceiptRegistration(
                        ticket.id(),
                        target.expoPushToken(),
                        notificationId
                ));
                log.debug("Expo Push ticket created: notificationId={}, receiptId={}", notificationId, ticket.id());
            } else {
                log.warn("Unexpected Expo Push ticket: notificationId={}, status={}",
                        notificationId, ticket.status());
            }
        }
        try {
            receiptService.trackAll(registrations);
        } catch (RuntimeException exception) {
            // Push는 이미 Expo에 접수됐으므로 Receipt 저장 실패로 재발송하지 않는다.
            log.warn("Failed to track Expo Push receipts: count={}, reason={}",
                    registrations.size(), exception.getMessage());
        }
    }

    private ExpoPushMessage toMessage(PushTarget target) {
        Notification notification = target.notification();
        return new ExpoPushMessage(
                target.expoPushToken(),
                notification.getTitle(),
                notification.getBody(),
                "default",
                "high",
                buildData(notification)
        );
    }

    private Map<String, Object> buildData(Notification notification) {
        return NotificationDataResponse.from(notification, readPayload(notification.getPayload())).toPushData();
    }

    private boolean isRetryable(RuntimeException exception) {
        if (exception instanceof RetryableExpoPushException
                || exception instanceof ResourceAccessException) {
            return true;
        }
        if (exception instanceof RestClientResponseException responseException) {
            return responseException.getStatusCode().value() == 429
                    || responseException.getStatusCode().is5xxServerError();
        }
        return false;
    }

    private void sleep(long delayMillis) {
        if (delayMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Expo Push retry was interrupted.", exception);
        }
    }

    private Map<String, Object> readPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> data = objectMapper.readValue(payload, PAYLOAD_TYPE);
            return data == null ? Map.of() : data;
        } catch (JsonProcessingException exception) {
            log.warn("Failed to parse Expo Push payload: reason={}", exception.getMessage());
            return Map.of();
        }
    }

    private record ExpoPushMessage(
            String to,
            String title,
            String body,
            String sound,
            String priority,
            Map<String, Object> data
    ) {
    }

    private record ExpoPushResponse(
            List<ExpoPushTicket> data,
            List<ExpoRequestError> errors
    ) {
    }

    private record ExpoPushTicket(
            String status,
            String id,
            String message,
            Map<String, Object> details
    ) {
        private String errorCode() {
            Object error = details == null ? null : details.get("error");
            return error == null ? null : error.toString();
        }
    }

    private record ExpoRequestError(
            String code,
            String message
    ) {
    }

    private static class RetryableExpoPushException extends RuntimeException {
        private RetryableExpoPushException(String message) {
            super(message);
        }
    }
}
