package com.bookwheel.server.notification.push;

import com.bookwheel.server.notification.entity.Notification;
import com.bookwheel.server.notification.dto.NotificationDataResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Sends persisted notifications through the Expo Push Service HTTP API.
 */
@Slf4j
@Component
public class ExpoPushSender implements PushSender {

    private static final int EXPO_MAX_BATCH_SIZE = 100;
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ExpoPushSender(
            @Qualifier("expoPushRestClient") RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
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
            sendChunk(validTargets.subList(start, end));
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
        }

        if (response.data() == null) {
            return;
        }

        for (int index = 0; index < response.data().size(); index++) {
            ExpoPushTicket ticket = response.data().get(index);
            Long notificationId = index < targets.size()
                    ? targets.get(index).notification().getId()
                    : null;
            if ("error".equals(ticket.status())) {
                log.warn("Expo Push ticket error: notificationId={}, error={}, message={}",
                        notificationId, ticket.errorCode(), ticket.message());
            } else {
                log.debug("Expo Push ticket created: notificationId={}, receiptId={}", notificationId, ticket.id());
            }
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
        return NotificationDataResponse.from(notification, readPayload(notification.getPayload())).toMap();
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
}
