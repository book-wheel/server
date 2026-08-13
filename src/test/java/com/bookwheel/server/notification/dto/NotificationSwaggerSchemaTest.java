package com.bookwheel.server.notification.dto;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings({"rawtypes", "unchecked"})
class NotificationSwaggerSchemaTest {

    @Test
    @DisplayName("Swagger 알림 data 스키마에 필수 이동 정보와 모든 알림별 payload 필드가 노출된다")
    void notificationDataSchemaExposesAllDocumentedFields() {
        Map<String, Schema> schemas = ModelConverters.getInstance()
                .readAll(NotificationResponse.class);

        assertThat(schemas).containsKeys("NotificationResponse", "NotificationDataResponse");
        assertThat(schemas.get("NotificationDataResponse").getProperties()).containsKeys(
                "notificationId",
                "type",
                "deepLink",
                "groupId",
                "applicantUserPK",
                "status",
                "postId",
                "likerUserPK",
                "commenterUserPK",
                "commentPreview",
                "reviewId",
                "isbn",
                "roundNumber",
                "daysLeft",
                "wheelStateId",
                "completedUserPK",
                "banType",
                "reasonMessage",
                "permanent",
                "releaseDate",
                "mail"
        );
    }

    @Test
    @DisplayName("Swagger 알림 설정 스키마는 Expo Push Token 필드를 사용한다")
    void notificationPreferenceSchemaUsesExpoPushToken() {
        Map<String, Schema> requestSchemas = ModelConverters.getInstance()
                .read(NotificationPreferenceUpdateRequest.class);
        Map<String, Schema> responseSchemas = ModelConverters.getInstance()
                .read(NotificationPreferenceResponse.class);

        assertThat(requestSchemas.get("NotificationPreferenceUpdateRequest").getProperties())
                .containsKeys("groupEnabled", "roundEnabled", "communityEnabled", "pushEnabled", "expoPushToken")
                .doesNotContainKey("fcmToken");
        assertThat(responseSchemas.get("NotificationPreferenceResponse").getProperties())
                .containsKey("expoPushToken")
                .doesNotContainKey("fcmToken");
    }
}
