package com.bookwheel.server.schedule.repository;

import com.bookwheel.server.schedule.entity.Round;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoundRepository extends JpaRepository<Round, String> {
    // 라운드 스케줄 재 생성 시, 그룹 단위로 라운드 전체 삭제
    @Modifying
    @Query("DELETE FROM Round r WHERE r.group.groupId = :groupId")
    void deleteByGroup_GroupId(String groupId);
    
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
}
