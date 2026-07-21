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
    @Query("""
            select case when count(g) > 0 then true else false end
            from Group g
            where g.groupName = :groupName
            and (g.groupState is null or g.groupState <> :deletedState)
            """)
    // 삭제된 모임은 이름을 재사용할 수 있고, 기존 null 상태 모임은 중복 검사에 포함한다.
    boolean existsNotDeletedByGroupName(
            @Param("groupName") String groupName,
            @Param("deletedState") State deletedState
    );

    @Query("""
            select case when count(g) > 0 then true else false end
            from Group g
            where g.groupName = :groupName
            and g.groupId <> :groupId
            and (g.groupState is null or g.groupState <> :deletedState)
            """)
    // 이름 수정 시 자기 자신과 삭제된 모임을 제외한 이름 중복만 확인한다.
    boolean existsNotDeletedByGroupNameAndGroupIdNot(
            @Param("groupName") String groupName,
            @Param("groupId") String groupId,
            @Param("deletedState") State deletedState
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Group g where g.groupId = :groupId")
    // 삭제·일정 변경·그룹 소유 데이터 등록을 같은 그룹 단위로 직렬화한다.
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
