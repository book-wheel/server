package com.bookwheel.server.user.repository;

import com.bookwheel.server.user.entity.S3DeletionTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface S3DeletionTaskRepository extends JpaRepository<S3DeletionTask, Long> {

    boolean existsByObjectKeyHash(String objectKeyHash);

    @Query("""
            select task.taskPK
            from S3DeletionTask task
            where task.nextAttemptAt <= :now
            order by task.nextAttemptAt asc, task.taskPK asc
            """)
    List<Long> findDueTaskPKs(@Param("now") LocalDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from S3DeletionTask task where task.taskPK = :taskPK")
    Optional<S3DeletionTask> findByTaskPKForUpdate(@Param("taskPK") Long taskPK);
}
