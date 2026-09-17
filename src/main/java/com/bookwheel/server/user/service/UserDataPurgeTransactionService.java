package com.bookwheel.server.user.service;

import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDataPurgeTransactionService {

    private static final String DELETED_OWNER_USER_PK = "SYSTEM_DELETED_OWNER";

    private final UserRepository userRepository;
    private final EntityManager entityManager;
    private final S3DeletionQueueService s3DeletionQueueService;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean purgeIfDue(String userPK) {
        User user = userRepository.findByUserPKForUpdate(userPK).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (user == null
                || Boolean.TRUE.equals(user.getIsActive())
                || user.getPurgeAt() == null
                || user.getPurgeAt().isAfter(now)) {
            return false;
        }

        Set<String> objectKeys = collectOwnedObjectKeys(userPK);
        // 참조 행을 지우기 전에 객체 키를 같은 DB 트랜잭션에 보존한다.
        s3DeletionQueueService.enqueueAll(userPK, objectKeys);

        List<Long> notificationIds = entityManager.createQuery("""
                        select notification.id
                        from Notification notification
                        where notification.recipientUserPK = :userPK
                           or notification.payload like :payloadPattern
                        """, Long.class)
                .setParameter("userPK", userPK)
                .setParameter("payloadPattern", "%" + userPK + "%")
                .getResultList();

        if (!notificationIds.isEmpty()) {
            entityManager.createQuery("""
                            delete from ExpoPushReceipt receipt
                            where receipt.notificationId in :notificationIds
                            """)
                    .setParameter("notificationIds", notificationIds)
                    .executeUpdate();
        }
        entityManager.createQuery("""
                        delete from Notification notification
                        where notification.recipientUserPK = :userPK
                           or notification.payload like :payloadPattern
                        """)
                .setParameter("userPK", userPK)
                .setParameter("payloadPattern", "%" + userPK + "%")
                .executeUpdate();
        entityManager.createQuery("delete from NotificationPreference preference where preference.userPK = :userPK")
                .setParameter("userPK", userPK)
                .executeUpdate();

        // 삭제 대상 메시지를 가리키던 읽음 위치는 같은 방의 이전 미삭제 메시지로 이동한다.
        // null로 초기화하면 남은 모든 메시지가 미읽음으로 재계산된다.
        entityManager.createNativeQuery("""
                        update chat_room_read_state read_state
                        join chat_message deleted_message
                          on deleted_message.chat_message_id = read_state.last_read_message_id
                        set read_state.last_read_message_id = (
                            select max(candidate.chat_message_id)
                            from chat_message candidate
                            where candidate.chat_room_id = deleted_message.chat_room_id
                              and candidate.chat_message_id < deleted_message.chat_message_id
                              and candidate.sender_user_pk <> :userPK
                        )
                        where deleted_message.sender_user_pk = :userPK
                        """)
                .setParameter("userPK", userPK)
                .executeUpdate();
        entityManager.createQuery("delete from ChatRoomReadState state where state.user.id = :userPK")
                .setParameter("userPK", userPK)
                .executeUpdate();
        entityManager.createQuery("delete from ChatMessage message where message.sender.id = :userPK")
                .setParameter("userPK", userPK)
                .executeUpdate();

        // 공개 게시글·댓글·리뷰와 첨부 이미지는 보존하고 작성자 연결만 해제한다.
        // 탈퇴자가 다른 콘텐츠에 남긴 좋아요·신고 기록은 개인 행동 기록이므로 삭제한다.
        adjustPostLikeCounts(userPK);
        entityManager.createQuery("delete from PostLike postLike where postLike.user.id = :userPK")
                .setParameter("userPK", userPK)
                .executeUpdate();
        entityManager.createQuery("delete from PostReport report where report.reporter.id = :userPK")
                .setParameter("userPK", userPK)
                .executeUpdate();
        entityManager.createQuery("update PostComment comment set comment.user = null where comment.user.id = :userPK")
                .setParameter("userPK", userPK)
                .executeUpdate();
        entityManager.createQuery("update Post post set post.uploader = null where post.uploader.id = :userPK")
                .setParameter("userPK", userPK)
                .executeUpdate();

        adjustReviewLikeCounts(userPK);
        entityManager.createQuery("delete from ReviewLike reviewLike where reviewLike.user.id = :userPK")
                .setParameter("userPK", userPK)
                .executeUpdate();
        entityManager.createQuery("update BookReview review set review.reviewer = null where review.reviewer.id = :userPK")
                .setParameter("userPK", userPK)
                .executeUpdate();
        entityManager.createQuery("delete from BookVote vote where vote.user.id = :userPK")
                .setParameter("userPK", userPK)
                .executeUpdate();
        entityManager.createQuery("delete from BookLike bookLike where bookLike.userPK = :userPK")
                .setParameter("userPK", userPK)
                .executeUpdate();

        // 멤버십과 소유 도서를 참조하는 배정 기록을 먼저 제거해야 FK 제약을 위반하지 않는다.
        entityManager.createQuery("""
                        delete from WheelStateImage image
                        where image.wheelState.wheelStateId in (
                            select state.wheelStateId
                            from WheelState state
                            where state.member.memberId in (
                                select member.memberId from Member member where member.user.id = :userPK
                            )
                        )
                        """)
                .setParameter("userPK", userPK)
                .executeUpdate();
        entityManager.createQuery("""
                        delete from WheelState state
                        where state.member.memberId in (
                            select member.memberId from Member member where member.user.id = :userPK
                        )
                        """)
                .setParameter("userPK", userPK)
                .executeUpdate();
        entityManager.createNativeQuery("""
                        update own_book
                        set owner_id = :deletedOwnerUserPK,
                            book_condition = null,
                            note_to_reader = null
                        where owner_id = :userPK
                        """)
                .setParameter("userPK", userPK)
                .setParameter("deletedOwnerUserPK", DELETED_OWNER_USER_PK)
                .executeUpdate();
        entityManager.createQuery("delete from Member member where member.user.id = :userPK")
                .setParameter("userPK", userPK)
                .executeUpdate();
        entityManager.createQuery("delete from Penalty penalty where penalty.user.id = :userPK")
                .setParameter("userPK", userPK)
                .executeUpdate();

        userRepository.delete(user);
        userRepository.flush();
        log.info("탈퇴 회원 영구 삭제 DB 처리 완료: userPK={}", userPK);
        return true;
    }

    private Set<String> collectOwnedObjectKeys(String userPK) {
        Set<String> objectKeys = new LinkedHashSet<>();
        objectKeys.addAll(entityManager.createQuery("""
                        select message.imageKey
                        from ChatMessage message
                        where message.sender.id = :userPK
                          and message.imageKey is not null
                        """, String.class)
                .setParameter("userPK", userPK)
                .getResultList());
        objectKeys.addAll(entityManager.createQuery("""
                        select image.objectKey
                        from WheelStateImage image
                        where image.wheelState.member.user.id = :userPK
                        """, String.class)
                .setParameter("userPK", userPK)
                .getResultList());
        objectKeys.removeIf(key -> !StringUtils.hasText(key));
        return objectKeys;
    }

    private void adjustPostLikeCounts(String userPK) {
        entityManager.createNativeQuery("""
                        update post target_post
                        join (
                            select post_id, count(*) as removed_count
                            from post_like
                            where user_id = :userPK
                            group by post_id
                        ) removed on removed.post_id = target_post.post_id
                        set target_post.like_count = greatest(
                            target_post.like_count - removed.removed_count,
                            0
                        )
                        """)
                .setParameter("userPK", userPK)
                .executeUpdate();
    }

    private void adjustReviewLikeCounts(String userPK) {
        entityManager.createNativeQuery("""
                        update book_review target_review
                        join (
                            select review_id, count(*) as removed_count
                            from review_like
                            where user_id = :userPK
                            group by review_id
                        ) removed on removed.review_id = target_review.review_id
                        set target_review.like_count = greatest(
                            target_review.like_count - removed.removed_count,
                            0
                        )
                        """)
                .setParameter("userPK", userPK)
                .executeUpdate();
    }
}
