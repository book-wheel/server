package com.bookwheel.server.notification.dto;

import com.bookwheel.server.notification.entity.Notification;
import com.bookwheel.server.notification.enums.NotificationType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.Map;

@Schema(
        name = "NotificationDataResponse",
        description = "GET /api/v1/notifications의 인앱 알림 data입니다. 알림 종류에 해당하는 선택 필드만 포함됩니다. "
                + "실제 Expo Push data는 개인정보 최소화를 위해 notificationId, type, deepLink만 기본 제공하고 "
                + "REVIEW_LIKED에만 isbn을 추가로 제공합니다."
)
@Builder
public record NotificationDataResponse(
        @Schema(description = "저장된 알림 ID. 인앱·Expo Push data 공통 필드", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        Long notificationId,

        @Schema(description = "알림 종류. 인앱·Expo Push data 공통 필드", example = "REVIEW_LIKED", requiredMode = Schema.RequiredMode.REQUIRED)
        NotificationType type,

        @Schema(
                description = "프론트엔드 이동 경로. 인앱·Expo Push data 공통 필드며 이동 대상이 없으면 null일 수 있습니다.",
                example = "/reviews/9",
                nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String deepLink,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "관련 모임 ID", example = "group-uuid", nullable = true)
        String groupId,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "가입 신청자 식별자", example = "applicant-user-pk", nullable = true)
        String applicantUserPK,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "가입 요청 처리 결과", example = "APPROVED", nullable = true)
        String status,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "관련 게시물 ID", example = "7", nullable = true)
        Long postId,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "좋아요 또는 공감을 누른 사용자 식별자", example = "liker-user-pk", nullable = true)
        String likerUserPK,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "댓글 작성자 식별자", example = "commenter-user-pk", nullable = true)
        String commenterUserPK,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "댓글 내용 미리보기", example = "저도 재미있게 읽었어요!", nullable = true)
        String commentPreview,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "관련 리뷰 ID", example = "9", nullable = true)
        Long reviewId,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "리뷰가 작성된 도서 ISBN. REVIEW_LIKED에서 인앱과 Expo Push data에 모두 포함", example = "9788954681179", nullable = true)
        String isbn,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "관련 라운드 번호", example = "2", nullable = true)
        Integer roundNumber,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "라운드 마감까지 남은 일수", example = "1", nullable = true)
        Integer daysLeft,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "완독 상태 식별자", example = "wheel-state-uuid", nullable = true)
        String wheelStateId,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "완독 인증 사용자 식별자", example = "completed-user-pk", nullable = true)
        String completedUserPK,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "계정 제재 종류", example = "SEVEN_DAYS", nullable = true)
        String banType,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "계정 제재 사유. 인앱 알림 전용이며 Expo Push data에는 포함하지 않음", example = "운영 정책 위반", nullable = true)
        String reasonMessage,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "영구 제재 여부", example = "true", nullable = true)
        Boolean permanent,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "제재 해제 예정 일시", example = "2026-08-21T12:00:00", nullable = true)
        String releaseDate,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "계정 알림 관련 이메일. 인앱 알림 전용이며 Expo Push data에는 포함하지 않음", example = "reader@example.com", nullable = true)
        String mail
) {

    public static NotificationDataResponse from(Notification notification, Map<String, Object> payload) {
        Map<String, Object> safePayload = payload == null ? Map.of() : payload;
        return NotificationDataResponse.builder()
                .notificationId(notification.getId())
                .type(notification.getType())
                .deepLink(notification.getDeepLink())
                .groupId(stringValue(safePayload, "groupId"))
                .applicantUserPK(stringValue(safePayload, "applicantUserPK"))
                .status(stringValue(safePayload, "status"))
                .postId(longValue(safePayload, "postId"))
                .likerUserPK(stringValue(safePayload, "likerUserPK"))
                .commenterUserPK(stringValue(safePayload, "commenterUserPK"))
                .commentPreview(stringValue(safePayload, "commentPreview"))
                .reviewId(longValue(safePayload, "reviewId"))
                .isbn(stringValue(safePayload, "isbn"))
                .roundNumber(integerValue(safePayload, "roundNumber"))
                .daysLeft(integerValue(safePayload, "daysLeft"))
                .wheelStateId(stringValue(safePayload, "wheelStateId"))
                .completedUserPK(stringValue(safePayload, "completedUserPK"))
                .banType(stringValue(safePayload, "banType"))
                .reasonMessage(stringValue(safePayload, "reasonMessage"))
                .permanent(booleanValue(safePayload, "permanent"))
                .releaseDate(stringValue(safePayload, "releaseDate"))
                .mail(stringValue(safePayload, "mail"))
                .build();
    }

    private static String stringValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }

    private static Long longValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Integer integerValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value instanceof Number number ? number.intValue() : null;
    }

    private static Boolean booleanValue(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value instanceof Boolean booleanValue ? booleanValue : null;
    }
}
