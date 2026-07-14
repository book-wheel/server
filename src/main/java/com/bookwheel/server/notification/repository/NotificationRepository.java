package com.bookwheel.server.notification.repository;

import com.bookwheel.server.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientUserPKOrderByCreatedAtDesc(String recipientUserPK, Pageable pageable);

    long countByRecipientUserPKAndIsReadFalse(String recipientUserPK);

    // 신규 컬럼 도입 전 payload만 가지고 있던 레거시 알림을 찾는다.
    List<Notification> findByGroupIdIsNull();

    @Modifying
    @Query("delete from Notification n where n.groupId = :groupId")
    // 새 스키마에서 그룹에 연결된 알림을 DB에서 일괄 삭제한다.
    int deleteAllByGroupId(@Param("groupId") String groupId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP " +
            "WHERE n.recipientUserPK = :recipientUserPK AND n.isRead = false")
    int markAllRead(@Param("recipientUserPK") String recipientUserPK);
}
