package com.bookwheel.server.notification.service;

import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.notification.entity.Notification;
import com.bookwheel.server.notification.entity.NotificationPreference;
import com.bookwheel.server.notification.enums.NotificationType;
import com.bookwheel.server.notification.event.NotificationEvent;
import com.bookwheel.server.notification.push.FcmSender;
import com.bookwheel.server.notification.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private NotificationPreferenceService preferenceService;

    @Mock
    private FcmSender fcmSender;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("모임 알림은 groupId를 저장한다")
    void createGroupNotificationStoresGroupId() {
        given(groupRepository.findByGroupIdForUpdate("group-1"))
                .willReturn(Optional.of(org.mockito.Mockito.mock(Group.class)));
        given(preferenceService.getOrInit("userPK"))
                .willReturn(NotificationPreference.defaultsFor("userPK"));
        given(notificationRepository.save(any(Notification.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Notification notification = notificationService.create(NotificationEvent.builder()
                .recipientUserPK("userPK")
                .type(NotificationType.GROUP_STARTED)
                .title("시작")
                .body("모임이 시작됐어요.")
                .deepLink("/groups/group-1")
                .groupId("group-1")
                .payload(java.util.Map.of("groupId", "group-1"))
                .build());

        assertThat(notification.getGroupId()).isEqualTo("group-1");
        then(groupRepository).should().findByGroupIdForUpdate("group-1");
    }

    @Test
    @DisplayName("삭제된 모임의 비동기 알림은 저장하지 않는다")
    void createSkipsNotificationWhenGroupDeleted() {
        given(groupRepository.findByGroupIdForUpdate("group-1")).willReturn(Optional.empty());

        Notification notification = notificationService.create(NotificationEvent.builder()
                .recipientUserPK("userPK")
                .type(NotificationType.GROUP_STARTED)
                .title("시작")
                .body("모임이 시작됐어요.")
                .deepLink("/groups/group-1")
                .groupId("group-1")
                .build());

        assertThat(notification).isNull();
        then(preferenceService).shouldHaveNoInteractions();
        then(notificationRepository).should(never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("기존 payload 기반 모임 알림도 삭제한다")
    void deleteByGroupIdDeletesLegacyPayloadNotification() {
        Notification legacyNotification = Notification.builder()
                .recipientUserPK("userPK")
                .type(NotificationType.GROUP_STARTED)
                .category(NotificationType.GROUP_STARTED.getCategory())
                .title("시작")
                .body("모임이 시작됐어요.")
                .payload("{\"groupId\":\"group-1\"}")
                .build();
        given(notificationRepository.findByGroupIdIsNull()).willReturn(List.of(legacyNotification));

        notificationService.deleteByGroupId("group-1");

        then(notificationRepository).should().deleteAllByGroupId("group-1");
        then(notificationRepository).should().delete(legacyNotification);
    }
}
