package com.bookwheel.server.notification.controller;

import com.bookwheel.server.notification.dto.NotificationPreferenceResponse;
import com.bookwheel.server.notification.dto.NotificationPreferenceUpdateRequest;
import com.bookwheel.server.notification.dto.NotificationResponse;
import com.bookwheel.server.notification.entity.Notification;
import com.bookwheel.server.notification.enums.NotificationType;
import com.bookwheel.server.notification.service.NotificationPreferenceService;
import com.bookwheel.server.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private NotificationPreferenceService preferenceService;

    @Test
    @WithMockUser(username = "userPK")
    @DisplayName("알림 설정 API에서 빈 문자열로 Expo Push Token을 해제한다")
    void updatePreferencesAcceptsTokenRemoval() throws Exception {
        given(preferenceService.update(eq("userPK"), any(NotificationPreferenceUpdateRequest.class)))
                .willReturn(new NotificationPreferenceResponse(true, true, true, true, null));

        mockMvc.perform(put("/api/v1/notifications/preferences")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"expoPushToken\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expoPushToken").isEmpty());

        verify(preferenceService).update(
                "userPK",
                new NotificationPreferenceUpdateRequest(null, null, null, null, "")
        );
    }

    @Test
    @WithMockUser(username = "userPK")
    @DisplayName("알림 설정 API는 Expo 형식이 아닌 토큰을 거절한다")
    void updatePreferencesRejectsInvalidToken() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/preferences")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"expoPushToken\":\"native-fcm-token\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "userPK")
    @DisplayName("알림 목록 응답의 data에 Swagger 계약과 동일한 이동 정보를 제공한다")
    void listReturnsTypedNotificationData() throws Exception {
        Notification notification = Notification.builder()
                .id(42L)
                .recipientUserPK("userPK")
                .type(NotificationType.REVIEW_LIKED)
                .category(NotificationType.REVIEW_LIKED.getCategory())
                .title("리뷰 공감")
                .body("책벌레님이 회원님의 리뷰에 공감했어요.")
                .deepLink("/reviews/9")
                .isRead(false)
                .createdAt(LocalDateTime.of(2026, 8, 14, 10, 30))
                .build();
        NotificationResponse response = NotificationResponse.from(
                notification,
                Map.of(
                        "reviewId", 9L,
                        "isbn", "9788954681179",
                        "likerUserPK", "liker-user-pk"
                )
        );
        given(notificationService.list(eq("userPK"), any()))
                .willReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].data.notificationId").value(42))
                .andExpect(jsonPath("$.data.content[0].data.type").value("REVIEW_LIKED"))
                .andExpect(jsonPath("$.data.content[0].data.deepLink").value("/reviews/9"))
                .andExpect(jsonPath("$.data.content[0].data.reviewId").value(9))
                .andExpect(jsonPath("$.data.content[0].data.isbn").value("9788954681179"))
                .andExpect(jsonPath("$.data.content[0].data.likerUserPK").value("liker-user-pk"))
                .andExpect(jsonPath("$.data.content[0].data.groupId").doesNotExist());
    }
}
