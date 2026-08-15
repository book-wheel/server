package com.bookwheel.server.notification.repository;

import com.bookwheel.server.notification.entity.ExpoPushReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ExpoPushReceiptRepository extends JpaRepository<ExpoPushReceipt, String> {

    List<ExpoPushReceipt> findTop1000ByCreatedAtBeforeOrderByCreatedAtAsc(Instant createdAt);

    long deleteByCreatedAtBefore(Instant createdAt);
}
