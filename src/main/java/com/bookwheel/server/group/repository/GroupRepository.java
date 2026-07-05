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

    // 모집 중인 그룹 중 시작일이 지났고 책 등록이 완료된 그룹을 진행 중으로 변경
    @Modifying
    @Query("UPDATE Group g SET g.groupState = :inProcess " +
            "WHERE g.groupState = :recruiting AND g.groupId IN :groupIds")
    int updateGroupStateToInProcessByGroupIds(
            @Param("inProcess") State inProgress,
            @Param("recruiting") State recruiting,
            @Param("groupIds") List<String> groupIds
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

    // 오늘 시작해야 하는데 아직 모집중인 그룹들 (알림 대상 후보)
    List<Group> findByGroupStateAndStartDateLessThanEqual(State state, LocalDate today);

    // 마지막 라운드 종료일이 today 이전인, IN_PROGRESS 그룹들 (종료 알림 대상)
    @Query("SELECT g FROM Group g WHERE g.groupState = :inProgress AND g.groupId IN (" +
            "    SELECT r.group.groupId FROM Round r " +
            "    WHERE r.endDate < :today AND r.roundNumber = g.groupRoundCount" +
            ")")
    List<Group> findGroupsBecomingComplete(@Param("inProgress") State inProgress, @Param("today") LocalDate today);
}
