package com.bookwheel.server.schedule.repository;

import com.bookwheel.server.group.enums.State;
import com.bookwheel.server.schedule.entity.Round;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoundRepository extends JpaRepository<Round, String> {
    // 라운드 스케줄 재 생성 시, 그룹 단위로 라운드 전체 삭제
    @Modifying
    @Query("DELETE FROM Round r WHERE r.group.groupId = :groupId")
    void deleteByGroup_GroupId(String groupId);

    @Modifying
    @Query("DELETE FROM Round r WHERE r.roundId IN :roundIds")
    void deleteByRoundIdIn(@Param("roundIds") Collection<String> roundIds);
    
    // 특정 라운드 조회 (현재 라운드 계산, 전체 일정 표시용)
    List<Round> findByGroup_GroupIdOrderByRoundNumberAsc(String groupId);

    // 진행 중 멤버 변동 시 시작 시점에 확정된 실행 범위의 라운드만 재배정 대상으로 조회한다.
    @Query("""
            select r
            from Round r
            where r.group.groupId = :groupId
              and r.roundNumber <= r.group.groupRoundCount
            order by r.roundNumber asc
            """)
    List<Round> findExecutableRoundsByGroupIdOrderByRoundNumberAsc(@Param("groupId") String groupId);

    // 기존 일정의 시작 당일 교체를 막을 때 실제로 생성된 날짜 틀이 있는지 확인한다.
    boolean existsByGroup_GroupId(String groupId);
    
    // 특정 회차 라운드 조회 (이전 회차 조회 등 단건 참조 로직용)
    Optional<Round> findByGroup_GroupIdAndRoundNumber(String groupId, Integer roundNumber);
    
    // 시작일/종료일이 모두 존재하는 '유효한' 라운드만 회차순으로 조회 (날짜 비교 시 null로 인한 NPE 방지용)
    List<Round> findByGroup_GroupIdAndStartDateIsNotNullAndEndDateIsNotNullOrderByRoundNumberAsc(String groupId);
    // 실제 실행 범위 안에 있는 진행 중 라운드만 마감 대상으로 조회한다.
    @Query("""
            select r.roundId
            from Round r
            where r.endDate < :date
              and r.group.groupState = :groupState
              and r.roundNumber <= r.group.groupRoundCount
            """)
    List<String> findExecutableRoundIdsByEndDateBefore(
            @Param("date") LocalDate date,
            @Param("groupState") State groupState
    );

    // 날짜 틀 중 실제 실행 범위 안에 있는 진행 중 라운드만 시작 대상으로 조회한다.
    @Query("""
            select r
            from Round r
            where r.startDate <= :date
              and r.endDate >= :date
              and r.group.groupState = :groupState
              and r.roundNumber <= r.group.groupRoundCount
            """)
    List<Round> findExecutableRoundsContainingDate(
            @Param("date") LocalDate date,
            @Param("groupState") State groupState
    );

    // 스케줄러가 그룹 잠금 뒤에도 라운드가 현재 일정으로 남아 있는지 DB 기준으로 다시 확인한다.
    @Query("""
            select case when count(r) > 0 then true else false end
            from Round r
            where r.roundId = :roundId
              and r.startDate <= :date
              and r.endDate >= :date
              and r.group.groupState = :groupState
              and r.roundNumber <= r.group.groupRoundCount
            """)
    boolean existsExecutableRoundContainingDate(
            @Param("roundId") String roundId,
            @Param("date") LocalDate date,
            @Param("groupState") State groupState
    );

    // 종료 알림과 마감 알림은 실제 실행 범위 안의 진행 중 라운드에만 발행한다.
    @Query("""
            select r
            from Round r
            where r.endDate = :endDate
              and r.group.groupState = :groupState
              and r.roundNumber <= r.group.groupRoundCount
            """)
    List<Round> findExecutableRoundsByEndDate(
            @Param("endDate") LocalDate endDate,
            @Param("groupState") State groupState
    );

    // 그룹의 현재 진행 중인 라운드 (오늘 날짜가 시작/종료일 사이) 조회
    @Query("SELECT r FROM Round r " +
            "WHERE r.group.groupId = :groupId " +
            "AND r.group.groupState = :groupState " +
            "AND r.roundNumber <= r.group.groupRoundCount " +
            "AND r.startDate <= :today AND r.endDate >= :today")
    Optional<Round> findCurrentRound(
            @Param("groupId") String groupId,
            @Param("today") LocalDate today,
            @Param("groupState") State groupState
    );
}
