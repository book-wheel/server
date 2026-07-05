package com.bookwheel.server.notification.repository;

import com.bookwheel.server.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientUserPKOrderByCreatedAtDesc(String recipientUserPK, Pageable pageable);

    long countByRecipientUserPKAndIsReadFalse(String recipientUserPK);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP " +
            "WHERE n.recipientUserPK = :recipientUserPK AND n.isRead = false")
    int markAllRead(@Param("recipientUserPK") String recipientUserPK);
}
