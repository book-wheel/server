package com.bookwheel.server.notification.service;

import com.bookwheel.server.community.entity.BookReview;
import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.repository.BookReviewRepository;
import com.bookwheel.server.community.repository.PostRepository;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.notification.entity.Notification;
import com.bookwheel.server.notification.entity.NotificationPreference;
import com.bookwheel.server.notification.enums.NotificationType;
import com.bookwheel.server.notification.event.BulkNotificationEvent;
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
    private PostRepository postRepository;

    @Mock
    private BookReviewRepository bookReviewRepository;

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
        Group deletedGroup = org.mockito.Mockito.mock(Group.class);
        given(deletedGroup.getGroupState()).willReturn(State.DELETED);
        given(groupRepository.findByGroupIdForUpdate("group-1")).willReturn(Optional.of(deletedGroup));

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

    @Test
    @DisplayName("게시물 알림은 대상 게시물을 잠그고 살아 있을 때만 저장한다")
    void createLocksPostBeforeSavingNotification() {
        given(postRepository.findByPostIdForUpdate(7L))
                .willReturn(Optional.of(org.mockito.Mockito.mock(Post.class)));
        given(preferenceService.getOrInit("userPK"))
                .willReturn(NotificationPreference.defaultsFor("userPK"));
        given(notificationRepository.save(any(Notification.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Notification notification = notificationService.create(postLikedEvent());

        assertThat(notification).isNotNull();
        assertThat(notification.getDeepLink()).isEqualTo("/posts/7");
        then(postRepository).should().findByPostIdForUpdate(7L);
    }

    @Test
    @DisplayName("알림 작업이 큐에 있는 사이 게시물이 먼저 삭제되면 알림을 다시 저장하지 않는다")
    void createSkipsNotificationWhenPostDeletedBeforeAsyncSave() {
        // 삭제 트랜잭션이 같은 게시물 행을 먼저 잠그고 지운 뒤라 여기서는 조회 결과가 비어 있다.
        given(postRepository.findByPostIdForUpdate(7L)).willReturn(Optional.empty());

        Notification notification = notificationService.create(postLikedEvent());

        assertThat(notification).isNull();
        then(preferenceService).shouldHaveNoInteractions();
        then(notificationRepository).should(never()).save(any(Notification.class));
        // 열 수 없는 링크를 푸시로 먼저 보내는 일도 없어야 한다.
        then(fcmSender).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("알림 작업이 큐에 있는 사이 리뷰가 먼저 삭제되면 알림을 다시 저장하지 않는다")
    void createSkipsNotificationWhenReviewDeletedBeforeAsyncSave() {
        given(bookReviewRepository.findByReviewIdForUpdate(3L)).willReturn(Optional.empty());

        Notification notification = notificationService.create(NotificationEvent.builder()
                .recipientUserPK("userPK")
                .type(NotificationType.REVIEW_LIKED)
                .title("리뷰 좋아요")
                .body("누군가 회원님의 리뷰에 좋아요를 눌렀어요.")
                .deepLink("/reviews/3")
                .payload(java.util.Map.of("reviewId", 3L))
                .build());

        assertThat(notification).isNull();
        then(preferenceService).shouldHaveNoInteractions();
        then(notificationRepository).should(never()).save(any(Notification.class));
        then(fcmSender).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("리뷰가 살아 있으면 리뷰 알림을 저장한다")
    void createSavesReviewNotificationWhenReviewAlive() {
        given(bookReviewRepository.findByReviewIdForUpdate(3L))
                .willReturn(Optional.of(org.mockito.Mockito.mock(BookReview.class)));
        given(preferenceService.getOrInit("userPK"))
                .willReturn(NotificationPreference.defaultsFor("userPK"));
        given(notificationRepository.save(any(Notification.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        Notification notification = notificationService.create(NotificationEvent.builder()
                .recipientUserPK("userPK")
                .type(NotificationType.REVIEW_LIKED)
                .title("리뷰 좋아요")
                .body("누군가 회원님의 리뷰에 좋아요를 눌렀어요.")
                .deepLink("/reviews/3")
                .build());

        assertThat(notification).isNotNull();
        assertThat(notification.getDeepLink()).isEqualTo("/reviews/3");
    }

    @Test
    @DisplayName("삭제된 게시물을 가리키는 벌크 알림은 한 건도 저장하지 않는다")
    void createBulkSkipsNotificationWhenPostDeletedBeforeAsyncSave() {
        given(postRepository.findByPostIdForUpdate(7L)).willReturn(Optional.empty());

        List<Notification> saved = notificationService.createBulk(BulkNotificationEvent.builder()
                .recipientUserPKs(List.of("userPK", "otherUserPK"))
                .type(NotificationType.POST_COMMENTED)
                .title("게시물 댓글")
                .body("회원님의 게시물에 댓글이 달렸어요.")
                .deepLink("/posts/7")
                .build());

        assertThat(saved).isEmpty();
        then(preferenceService).shouldHaveNoInteractions();
        then(notificationRepository).should(never()).saveAll(org.mockito.ArgumentMatchers.anyList());
        then(fcmSender).shouldHaveNoInteractions();
    }

    private NotificationEvent postLikedEvent() {
        return NotificationEvent.builder()
                .recipientUserPK("userPK")
                .type(NotificationType.POST_LIKED)
                .title("게시물 좋아요")
                .body("누군가 회원님의 게시물에 좋아요를 눌렀어요.")
                .deepLink("/posts/7")
                .payload(java.util.Map.of("postId", 7L))
                .build();
    }
}
