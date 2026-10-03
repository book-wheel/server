package com.bookwheel.server.common.oauth2.apple;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppleOAuthCredentialRepository extends JpaRepository<AppleOAuthCredential, String> {

    @Query("""
            select credential.userPK
            from AppleOAuthCredential credential
            where credential.revocationRequested = true
              and credential.nextAttemptAt <= :now
            order by credential.nextAttemptAt asc, credential.userPK asc
            """)
    List<String> findDueUserPKs(@Param("now") LocalDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select credential from AppleOAuthCredential credential where credential.userPK = :userPK")
    Optional<AppleOAuthCredential> findByUserPKForUpdate(@Param("userPK") String userPK);
}
