package com.bookwheel.server.notification.dto;

import com.bookwheel.server.notification.entity.Notification;
import com.bookwheel.server.notification.enums.NotificationType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.LinkedHashMap;
import java.util.Map;

@Schema(
        name = "NotificationDataResponse",
        description = "푸시 및 인앱 알림 화면 이동에 사용하는 데이터입니다. 알림 종류에 해당하는 선택 필드만 포함됩니다."
)
public record NotificationDataResponse(
        @Schema(description = "저장된 알림 ID", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        Long notificationId,

        @Schema(description = "알림 종류", example = "REVIEW_LIKED", requiredMode = Schema.RequiredMode.REQUIRED)
        NotificationType type,

        @Schema(
                description = "프론트엔드 이동 경로. 이동 대상이 없는 알림은 null일 수 있습니다.",
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
        @Schema(description = "리뷰가 작성된 도서 ISBN", example = "9788954681179", nullable = true)
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
        @Schema(description = "계정 제재 사유", example = "운영 정책 위반", nullable = true)
        String reasonMessage,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "영구 제재 여부", example = "true", nullable = true)
        Boolean permanent,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "제재 해제 예정 일시", example = "2026-08-21T12:00:00", nullable = true)
        String releaseDate,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "계정 알림 관련 이메일", example = "reader@example.com", nullable = true)
        String mail
) {

    public static NotificationDataResponse from(Notification notification, Map<String, Object> payload) {
        Map<String, Object> safePayload = payload == null ? Map.of() : payload;
        return new NotificationDataResponse(
                notification.getId(),
                notification.getType(),
                notification.getDeepLink(),
                stringValue(safePayload, "groupId"),
                stringValue(safePayload, "applicantUserPK"),
                stringValue(safePayload, "status"),
                longValue(safePayload, "postId"),
                stringValue(safePayload, "likerUserPK"),
                stringValue(safePayload, "commenterUserPK"),
                stringValue(safePayload, "commentPreview"),
                longValue(safePayload, "reviewId"),
                stringValue(safePayload, "isbn"),
                integerValue(safePayload, "roundNumber"),
                integerValue(safePayload, "daysLeft"),
                stringValue(safePayload, "wheelStateId"),
                stringValue(safePayload, "completedUserPK"),
                stringValue(safePayload, "banType"),
                stringValue(safePayload, "reasonMessage"),
                booleanValue(safePayload, "permanent"),
                stringValue(safePayload, "releaseDate"),
                stringValue(safePayload, "mail")
        );
    }

    public Map<String, Object> toMap() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("notificationId", notificationId);
        data.put("type", type.name());
        data.put("deepLink", deepLink);
        putIfNotNull(data, "groupId", groupId);
        putIfNotNull(data, "applicantUserPK", applicantUserPK);
        putIfNotNull(data, "status", status);
        putIfNotNull(data, "postId", postId);
        putIfNotNull(data, "likerUserPK", likerUserPK);
        putIfNotNull(data, "commenterUserPK", commenterUserPK);
        putIfNotNull(data, "commentPreview", commentPreview);
        putIfNotNull(data, "reviewId", reviewId);
        putIfNotNull(data, "isbn", isbn);
        putIfNotNull(data, "roundNumber", roundNumber);
        putIfNotNull(data, "daysLeft", daysLeft);
        putIfNotNull(data, "wheelStateId", wheelStateId);
        putIfNotNull(data, "completedUserPK", completedUserPK);
        putIfNotNull(data, "banType", banType);
        putIfNotNull(data, "reasonMessage", reasonMessage);
        putIfNotNull(data, "permanent", permanent);
        putIfNotNull(data, "releaseDate", releaseDate);
        putIfNotNull(data, "mail", mail);
        return data;
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

    private static void putIfNotNull(Map<String, Object> data, String key, Object value) {
        if (value != null) {
            data.put(key, value);
        }
    }
}
