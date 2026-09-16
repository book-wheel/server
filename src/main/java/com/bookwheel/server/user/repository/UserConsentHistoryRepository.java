package com.bookwheel.server.user.repository;

import com.bookwheel.server.user.entity.UserConsentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;

public interface UserConsentHistoryRepository extends JpaRepository<UserConsentHistory, Long> {

    List<UserConsentHistory> findAllBySubjectIdentifierHashInOrderByAgreedAtAsc(
            Collection<String> subjectIdentifierHashes
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UserConsentHistory history
               set history.withdrawalRequestedAt = :withdrawalRequestedAt,
                   history.retentionUntil = :retentionUntil
             where history.userPK = :userPK
            """)
    int scheduleRetentionByUserPK(
            @Param("userPK") String userPK,
            @Param("withdrawalRequestedAt") LocalDateTime withdrawalRequestedAt,
            @Param("retentionUntil") LocalDateTime retentionUntil
    );

    long deleteByRetentionUntilLessThanEqual(LocalDateTime now);

    long deleteByUserPK(String userPK);
}
