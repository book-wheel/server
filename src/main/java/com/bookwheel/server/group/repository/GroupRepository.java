package com.bookwheel.server.group.repository;

import com.bookwheel.server.group.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, String>, JpaSpecificationExecutor<Group> {
    boolean existsByGroupName(String groupName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Group g where g.groupId = :groupId")
    Optional<Group> findByGroupIdForUpdate(@Param("groupId") String groupId);
}
