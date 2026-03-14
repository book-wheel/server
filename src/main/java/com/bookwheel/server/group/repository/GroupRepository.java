package com.bookwheel.server.group.repository;

import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.enums.State;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, String>, JpaSpecificationExecutor<Group> {
    boolean existsByGroupName(String groupName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Group g where g.groupId = :groupId")
    Optional<Group> findByGroupIdForUpdate(@Param("groupId") String groupId);

    // 그룹 시작일이 지났으면서 모집 중인 그룹들을 진행중으로 변경
    @Modifying
    @Query("UPDATE Group g SET g.groupState = :inProcess " +
    "WHERE g.groupState = :recruiting AND g.startDate <= :today")
    int updateGroupStateToInProcess(
            @Param("inProcess") State inProgress,
            @Param("recruiting") State recruiting,
            @Param("today") LocalDate today
    );

    // 마지막 라운드인데(roundNumber와 groupRoundCount가 동일), 그 라운드의 endDate가 오늘보다 과거라면 종료된 그룹으로 변경
    @Modifying
    @Query("UPDATE Group g SET g.groupState = :completed " +
            "WHERE g.groupState = :inProgress " +
            "AND g.groupId IN (" +
            "    SELECT r.group.groupId FROM Round r " +
            "    WHERE r.endDate < :today AND r.roundNumber = g.groupRoundCount" +
            ")")
    int updateFinishedGroupsToComplete(
            @Param("completed") State completed,
            @Param("inProgress") State inProgress,
            @Param("today") LocalDate today
    );
}
