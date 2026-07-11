package com.bookwheel.server.schedule.repository;

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
    
    // 특정 회차 라운드 조회 (이전 회차 조회 등 단건 참조 로직용)
    Optional<Round> findByGroup_GroupIdAndRoundNumber(String groupId, Integer roundNumber);
    
    // 시작일/종료일이 모두 존재하는 '유효한' 라운드만 회차순으로 조회 (날짜 비교 시 null로 인한 NPE 방지용)
    List<Round> findByGroup_GroupIdAndStartDateIsNotNullAndEndDateIsNotNullOrderByRoundNumberAsc(String groupId);
    // 오늘 날짜보다 종료일이 이전인 Round들의 ID 목록 가져옴
    @Query("SELECT r.roundId FROM Round r WHERE r.endDate < :date")
    List<String> findRoundIdsByEndDateBefore(LocalDate date);

    List<Round> findByStartDate(LocalDate startDate);

    // 오늘 날짜가 포함된 라운드 조회 (시작일을 놓친 경우에도 현재 라운드 책바퀴 생성 가능)
    List<Round> findByStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate startDate, LocalDate endDate);

    // 종료일이 특정 날짜인 라운드 조회 (어제 종료된 라운드 → 미완독 알림)
    List<Round> findByEndDate(LocalDate endDate);

    // 종료일이 [from, to] 사이인 라운드 조회 (D-1/D-3 리마인더용)
    List<Round> findByEndDateBetween(LocalDate from, LocalDate to);

    // 그룹의 현재 진행 중인 라운드 (오늘 날짜가 시작/종료일 사이) 조회
    @Query("SELECT r FROM Round r " +
            "WHERE r.group.groupId = :groupId " +
            "AND r.startDate <= :today AND r.endDate >= :today")
    Optional<Round> findCurrentRound(@Param("groupId") String groupId, @Param("today") LocalDate today);
}
