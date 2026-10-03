package com.bookwheel.server.user.repository;

import com.bookwheel.server.user.entity.UserBlock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    boolean existsByBlocker_IdAndBlocked_Id(String blockerUserPK, String blockedUserPK);

    @EntityGraph(attributePaths = "blocked")
    Page<UserBlock> findAllByBlocker_IdOrderByCreatedAtDescBlockIdDesc(String blockerUserPK, Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        delete from UserBlock b
        where b.blocker.id = :blockerUserPK
        and b.blocked.id = :blockedUserPK
        """)
    int deleteBlock(
        @Param("blockerUserPK") String blockerUserPK,
        @Param("blockedUserPK") String blockedUserPK
    );
}
