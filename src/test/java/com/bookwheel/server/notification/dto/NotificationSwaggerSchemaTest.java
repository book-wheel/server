package com.bookwheel.server.notification.dto;

import com.bookwheel.server.notification.controller.NotificationController;
import com.bookwheel.server.notification.push.ExpoPushToken;
import com.bookwheel.server.user.controller.UserController;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.Operation;
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
        Schema notificationDataSchema = schemas.get("NotificationDataResponse");
        assertThat(notificationDataSchema.getDescription())
                .contains("GET /api/v1/notifications")
                .contains("notificationId, type, deepLink")
                .contains("REVIEW_LIKED")
                .contains("isbn");
        assertThat(notificationDataSchema.getProperties()).containsKeys(
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

        Schema requestTokenSchema = (Schema) requestSchemas.get("NotificationPreferenceUpdateRequest")
                .getProperties().get("expoPushToken");
        assertThat(requestTokenSchema.getDescription())
                .contains("ExpoPushToken[...]", "ExponentPushToken[...]", "UUID")
                .contains("이전 소유자")
                .contains("빈 문자열");
        assertThat(requestTokenSchema.getPattern()).isEqualTo("^$|" + ExpoPushToken.REGEX);

        Schema responseTokenSchema = (Schema) responseSchemas.get("NotificationPreferenceResponse")
                .getProperties().get("expoPushToken");
        assertThat(responseTokenSchema.getDescription())
                .contains("로그아웃", "DeviceNotRegistered", "null");
    }

    @Test
    @DisplayName("Swagger 알림 설정·로그아웃 API 설명이 최종 Expo Push 수명주기를 반영한다")
    void notificationOperationsDescribeFinalExpoPushLifecycle() throws NoSuchMethodException {
        Operation updatePreferences = NotificationController.class.getMethod(
                "updatePreferences",
                NotificationPreferenceUpdateRequest.class,
                Object.class
        ).getAnnotation(Operation.class);
        Operation list = NotificationController.class.getMethod(
                "list",
                org.springframework.data.domain.Pageable.class,
                Object.class
        ).getAnnotation(Operation.class);
        Operation logout = UserController.class.getMethod("logout", Object.class)
                .getAnnotation(Operation.class);

        assertThat(updatePreferences.description())
                .contains("UUID", "이전 사용자 귀속", "빈 문자열", "null");
        assertThat(list.description())
                .contains("Expo Push data", "notificationId, type, deepLink", "isbn");
        assertThat(logout.description())
                .contains("Refresh Token", "Expo Push Token", "함께 해제");
    }
}
